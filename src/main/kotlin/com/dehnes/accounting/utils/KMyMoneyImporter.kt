package com.dehnes.accounting.utils

import com.dehnes.accounting.bank.importers.SupportedImporters
import com.dehnes.accounting.database.*
import com.dehnes.accounting.database.Transactions.readTx
import com.dehnes.accounting.database.Transactions.writeTx
import com.dehnes.accounting.services.Categories
import com.dehnes.accounting.services.CategoryService
import com.dehnes.accounting.services.RootCategory
import com.dehnes.smarthome.utils.DateTimeUtils.zoneId
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.time.LocalDate
import java.util.*
import javax.sql.DataSource
import javax.xml.parsers.DocumentBuilderFactory


class KMyMoneyImporter(
    private val repository: Repository,
    private val dataSource: DataSource,
    private val categoryService: CategoryService,
) {

    fun import(
        userId: String,
        xml: String,
        defaultPayeeName: String,
        bankaccountToLedgerIdMapper: (a: Account) -> String,
        banknameToImporterFunctionMapper: (bankName: String) -> SupportedImporters?,
    ) {
        val document = parseXMl(xml)

        val kMyMoneyRoot = children(document.documentElement).map { parseStructure(it) }

        // keep track of all IDs
        val idMapping = mutableMapOf<String, String>()

        // import all accounts -> Categories
        val accounts = importAccounts(kMyMoneyRoot, idMapping, userId)

        // import all banks and bank accounts
        val bankAndAccounts = importBanks(kMyMoneyRoot, accounts, idMapping, userId, bankaccountToLedgerIdMapper, banknameToImporterFunctionMapper)

        // import all payees, they become accounts under a special root-account
        val (payees, defaultPayee) = importPayeesAsAccounts(kMyMoneyRoot, userId, idMapping, defaultPayeeName)

        val transactions = parseTransactions(kMyMoneyRoot)

        // import transactions and create bookings
        importTransactions(
            dataSource.readTx { categoryService.get(it) },
            bankAndAccounts,
            transactions,
            idMapping,
            payees,
            accounts,
            userId,
            defaultPayee
        )
    }

    private fun importAccounts(
        kMyMoneyRoot: List<XmlElement>,
        idMapping: MutableMap<String, String>,
        userId: String
    ): List<Account> {
        // Import all accounts (Categories)
        val accounts = kMyMoneyRoot.first { it.name == "ACCOUNTS" }.children.map { account ->
            val closed = account.children
                .firstOrNull { it.name == "KEYVALUEPAIRS" }
                ?.children
                ?.firstOrNull { it.attributes["key"] == "mm-closed" && it.attributes["value"] == "yes" } != null
            Account(
                account.attributes["id"]!!,
                account.attributes["parentaccount"]?.ifBlank { null },
                account.attributes["type"]!!,
                account.attributes["opened"]?.ifBlank { null }?.let { LocalDate.parse(it) },
                account.attributes["name"]!!,
                account.attributes["number"]?.ifBlank { null },
                account.attributes["description"]?.ifBlank { null },
                account.children.firstOrNull { it.name == "SUBACCOUNTS" }?.children?.map { it.attributes["id"]!! }
                    ?: emptyList(),
                closed
            )
        }

        fun accountChildren(parentId: String?): List<AccountTree> = accounts.filter { it.parentId == parentId }.map {
            AccountTree(
                it.id,
                it.type,
                it.opened,
                it.name,
                it.description,
                accountChildren(it.id)
            )
        }


        val accountTree = accounts.filter { it.parentId == null }.map {
            AccountTree(
                it.id,
                it.type,
                it.opened,
                it.name,
                it.description,
                accountChildren(it.id)
            )
        }

        return dataSource.writeTx { conn ->

            fun addCategory(a: AccountTree, parentId: String?) {
                val newId = UUID.nameUUIDFromBytes(a.id.toByteArray()).toString().apply {
                    idMapping.addNewId(a.id, this)
                }

                repository.addOrReplaceCategory(
                    conn,
                    userId,
                    CategoryDto(
                        newId,
                        a.name,
                        null,
                        parentId
                    )
                )

                a.subAccounts.forEach { child ->
                    addCategory(child, newId)
                }

            }

            accountTree.forEach { root ->

                val rootCategory = RootCategory.entries.first { it.name == root.name }

                repository.addOrReplaceCategory(
                    conn,
                    userId,
                    CategoryDto(
                        rootCategory.id,
                        rootCategory.name,
                        null,
                        null
                    )
                )

                root.subAccounts.forEach { child ->
                    addCategory(child, rootCategory.id)
                }
            }

            accounts
        }
    }

    private fun importTransactions(
        categories: Categories,
        bankAndAccounts: List<BankAccountDto>,
        transactions: List<KTransaction>,
        idMapping: MutableMap<String, String>,
        payees: List<Payee>,
        accounts: List<Account>,
        userId: String,
        defaultPayee: Payee
    ) {
        bankAndAccounts.forEach { bAccount ->

            dataSource.writeTx { conn ->

                // already imported?
                if (repository.getBankAccount(conn, bAccount.id)!!.transactionsCounter > 0) return@writeTx

                val txs = transactions
                    .filter { it.splits.any { it.accountId == idMapping[bAccount.id] } }

                txs.forEach { tx ->
                    val mainTx = tx.splits.single { it.accountId == idMapping[bAccount.id] }
                    val targetAccounts = tx.splits.filterNot { it.id == mainTx.id }

                    val transferOtherBankAccount =
                        bankAndAccounts.firstOrNull { ba -> idMapping[ba.id] == targetAccounts.first().accountId }

                    val parties = targetAccounts
                        .filter { it.payeeId != null }
                        .map { a -> payees.first { it.id == a.payeeId } }
                        .joinToString(",") { it.name }
                        .ifBlank { null }

                    val memos = targetAccounts.mapNotNull { it.memo }

                    val text = when {
                        memos.isNotEmpty() -> memos.joinToString("\n")
                        else -> targetAccounts
                            .map { a -> accounts.first { it.id == a.accountId } }
                            .joinToString("\n") { it.name }
                    }

                    // bank-tx
                    val bankTxId = repository.addBankTransaction(
                        conn,
                        userId,
                        BankTransactionAdd(
                            listOfNotNull(parties, text).joinToString(separator = ": "),
                            bAccount.ledgerId,
                            bAccount.bankId,
                            bAccount.id,
                            tx.postDate.atStartOfDay().atZone(zoneId).toInstant(),
                            mainTx.amountInCents.toLong(),
                        )
                    )

                    val alreadyBookedCandidate = if (transferOtherBankAccount != null) {
                        repository.getBookings(
                            conn,
                            categories,
                            bAccount.ledgerId,
                            Int.MAX_VALUE,
                            DateRangeFilter(
                                tx.postDate.atStartOfDay().atZone(zoneId).toInstant(),
                                tx.postDate.plusDays(1).atStartOfDay().atZone(zoneId).toInstant()
                            )
                        ).firstOrNull { it.description == tx.id }
                    } else null

                    // ledger booking
                    if (alreadyBookedCandidate != null) {
                        // find the other record which is not matched yet
                        val r = alreadyBookedCandidate.records.first { it.category.id == idMapping[mainTx.accountId]!! }
                        repository.matchBankTransaction(
                            conn,
                            userId,
                            bAccount.id,
                            bankTxId,
                            bAccount.ledgerId,
                            alreadyBookedCandidate.id,
                            r.id
                        )
                    } else {
                        repository.addBooking(conn, userId, BookingAdd(
                            bAccount.ledgerId,
                            transferOtherBankAccount?.let { tx.id },
                            tx.postDate.atStartOfDay().atZone(zoneId).toInstant(),
                            listOf(
                                BookingRecordAdd(
                                    mainTx.memo,
                                    when {
                                        transferOtherBankAccount != null -> idMapping[mainTx.accountId]!!
                                        else -> idMapping[mainTx.payeeId ?: defaultPayee.id]!!
                                    },
                                    mainTx.amountInCents.toLong(),
                                    bAccount.id,
                                    bankTxId
                                )
                            ) + targetAccounts.map {
                                BookingRecordAdd(
                                    it.memo,
                                    idMapping[it.accountId]!!,
                                    it.amountInCents.toLong(),
                                    null,
                                    null,
                                )
                            }
                        ))
                    }

                }
            }
        }
    }

    private fun parseTransactions(kMyMoneyRoot: List<XmlElement>): List<KTransaction> {
        val transactions = kMyMoneyRoot.first { it.name == "TRANSACTIONS" }.children.map {
            val splits = it.children.first { it.name == "SPLITS" }.children.map {
                check(it.attributes["action"] == "")
                check(it.attributes["price"] == "1/1")
                check(it.attributes["shares"] == it.attributes["value"])
                KTransactionSplit(
                    it.attributes["id"]!!,
                    it.attributes["number"]?.ifBlank { null }?.toInt(),
                    it.attributes["payee"]?.ifBlank { null },
                    it.attributes["account"]!!,
                    it.attributes["memo"]?.ifBlank { null },
                    valueToAmountInCents(it.attributes["value"]!!)
                )
            }

            check(it.attributes["memo"] == "")

            KTransaction(
                it.attributes["id"]!!,
                it.attributes["postdate"]!!.let { LocalDate.parse(it) },
                it.attributes["entrydate"]!!.let { LocalDate.parse(it) },
                splits
            )
        }
        return transactions
    }

    private fun importPayeesAsAccounts(
        kMyMoneyRoot: List<XmlElement>,
        userId: String,
        idMapping: MutableMap<String, String>,
        defaultPayeeName: String,
    ): Pair<List<Payee>, Payee> {

        val payees = kMyMoneyRoot.first { it.name == "PAYEES" }.children.map {
            Payee(
                it.attributes["id"]!!,
                it.attributes["name"]!!,
            )
        }

        val partyRootCategory = CategoryDto(
            RootCategory.Parties.id,
            RootCategory.Parties.name,
            null,
            null
        )
        dataSource.writeTx { conn ->
            repository.addOrReplaceCategory(conn, userId, partyRootCategory)
            payees.forEach { p ->
                repository.addOrReplaceCategory(
                    conn,
                    userId,
                    CategoryDto(
                        UUID.nameUUIDFromBytes(p.id.toByteArray()).toString().apply {
                            idMapping.addNewId(p.id, this)
                        },
                        p.name,
                        null,
                        partyRootCategory.id
                    )
                )
            }
        }

        return payees to (payees.firstOrNull { it.name == defaultPayeeName }
            ?: error("Could not find defaultPayeeName=$defaultPayeeName"))
    }

    private fun importBanks(
        kMyMoneyRoot: List<XmlElement>,
        accounts: List<Account>,
        idMapping: MutableMap<String, String>,
        userId: String,
        bankaccountToLedgerIdMapper: (a: Account) -> String,
        banknameToImporterFunctionMapper: (bankName: String) -> SupportedImporters?
    ): List<BankAccountDto> {
        val banksAndAccounts = kMyMoneyRoot.first { it.name == "INSTITUTIONS" }.children.map { bank ->
            val accountIds =
                bank.children.firstOrNull { it.name == "ACCOUNTIDS" }?.children?.map { it.attributes["id"]!! }
                    ?: emptyList()

            KBank(
                bank.attributes["id"]!!,
                bank.attributes["name"]!!,
                accountIds.map { aId -> accounts.first { it.id == aId } }
            )
        }


        // import Banks
        return dataSource.writeTx { conn ->

            banksAndAccounts.flatMap { bank ->
                val bankDto = BankDto(
                    UUID.nameUUIDFromBytes(bank.id.toByteArray()).toString().apply {
                        idMapping.addNewId(bank.id, this)
                    },
                    bank.name,
                    null,
                    banknameToImporterFunctionMapper(bank.name)?.name,
                )
                repository.addOrReplaceBank(conn, userId, bankDto)

                bank.accounts.map { bAccount ->
                    val bAccountId = UUID.nameUUIDFromBytes(bAccount.id.toByteArray()).toString().apply {
                        idMapping.addNewId(bAccount.id, this)
                    }

                    repository.getBankAccount(conn, bAccountId) ?: run {
                        val bankAccount = BankAccountDto(
                            bAccountId,
                            bAccount.name,
                            null,
                            bankaccountToLedgerIdMapper(bAccount),
                            bankDto.id,
                            bAccount.number ?: "unknown",
                            bAccount.opened!!,
                            if (bAccount.closed) LocalDate.now() else null,
                            0,
                            0,
                            0,
                            0,
                        )
                        repository.addBankAccount(conn, userId, bankAccount)
                        bankAccount
                    }
                }

            }
        }
    }

    fun valueToAmountInCents(value: String): Int {
        val (numerator, denominator) = value.split("/").map { it.trim().toInt() }
        val multiplyer = 100 / denominator
        return numerator * multiplyer
    }

    private fun parseStructure(node: Node): XmlElement {
        val c = children(node).map { parseStructure(it) }

        val attributes = node.attributes
        val attrs = if (attributes.length > 0) {
            (0..attributes.length).mapNotNull {
                val item = attributes.item(it) as Attr? ?: return@mapNotNull null
                item.name to item.value
            }.toMap()
        } else {
            emptyMap()
        }

        return XmlElement(
            node.localName,
            attrs,
            node.textContent.trim().ifBlank { null },
            c
        )
    }

    private fun parseXMl(xmlStr: String): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        val builder = factory.newDocumentBuilder()
        val source = InputSource(StringReader(xmlStr))
        return builder.parse(source)
    }

    private fun children(node: Node): List<Node> {
        val l = mutableListOf<Node>()
        val childNodes = node.childNodes
        if (childNodes.length > 0) {
            (0..childNodes.length).forEach {
                l.add(childNodes.item(it) ?: return@forEach)
            }
        }
        return l.filter { it.nodeType == Node.ELEMENT_NODE }
    }


    data class KBank(
        val id: String,
        val name: String,
        val accounts: List<Account>,
    )

    data class KTransaction(
        val id: String,
        val postDate: LocalDate,
        val entryDate: LocalDate,
        val splits: List<KTransactionSplit>,
    )

    data class KTransactionSplit(
        val id: String,
        val number: Int?,
        val payeeId: String?,
        val accountId: String,
        val memo: String?,
        val amountInCents: Int,
    )

    data class XmlElement(
        val name: String,
        val attributes: Map<String, String>,
        val text: String?,
        val children: List<XmlElement>
    )

    data class Account(
        val id: String,
        val parentId: String?,
        val type: String,
        val opened: LocalDate?,
        val name: String,
        val number: String?,
        val description: String?,
        val subAccounts: List<String>,
        val closed: Boolean,
    )

    data class AccountTree(
        val id: String,
        val type: String,
        val opened: LocalDate?,
        val name: String,
        val description: String?,
        val subAccounts: List<AccountTree>,
    )

    data class Payee(
        val id: String,
        val name: String
    )

}

fun MutableMap<String, String>.addNewId(id: String, id2: String) {
    var r = this.put(id, id2)
    check(r == null || r == id2)
    r = this.put(id2, id)
    check(r == null || r == id)
}