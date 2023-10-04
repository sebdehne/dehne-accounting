package com.dehnes.accounting.utils

import com.dehnes.accounting.bank.importers.SupportedImporters
import com.dehnes.accounting.database.*
import com.dehnes.accounting.database.Transactions.readTx
import com.dehnes.accounting.database.Transactions.writeTx
import com.dehnes.accounting.services.CategoryService
import com.dehnes.smarthome.utils.DateTimeUtils.zoneId
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.sql.Connection
import java.time.LocalDate
import java.util.*
import javax.sql.DataSource
import javax.xml.parsers.DocumentBuilderFactory

class KMyMoneyImporter(
    private val repository: Repository,
    private val dataSource: DataSource,
    private val categoryService: CategoryService,
) {

    internal class CategoryImporter(
        categories: List<AccountTree>,
        private val repository: Repository,
        private val userId: String,
        private val ledger: LedgerDto,
        private val payees: List<Payee>,
        private val payeeParent: CategoryDto,
    ) {

        private val accountIdMapping: MutableMap<String, Pair<CategoryDto, AccountTree>> = mutableMapOf()
        private val payeeIdMapping: MutableMap<String, Pair<CategoryDto, Payee>> = mutableMapOf()

        private fun asList(categories: List<AccountTree>) = categories.flatMap { asList(categories, it) }
        private fun asList(categories: List<AccountTree>, a: AccountTree): List<AccountTree> =
            a.subAccounts.flatMap { asList(categories, it) } + a

        private val categoriesList = asList(categories)

        fun getAccountWithoutImporting(accountId: String): AccountTree {
            return categoriesList.first { it.id == accountId }
        }

        fun getImportedPayeeByName(connection: Connection, payeeName: String): Pair<CategoryDto, Payee> =
            getImportedPayee(connection, payees.single { it.name == payeeName }.id)

        fun getImportedPayee(connection: Connection, payeeId: String): Pair<CategoryDto, Payee> {
            payeeIdMapping[payeeId]?.let { return it }

            val payee = payees.single { it.id == payeeId }

            val categoryDto = CategoryDto(
                UUID.nameUUIDFromBytes(payee.id.toByteArray()).toString(),
                payee.name,
                null,
                payeeParent.id,
                ledger.id
            )

            payeeIdMapping[payeeId] = (categoryDto to payee)

            repository.addOrReplaceCategory(
                connection,
                userId,
                categoryDto
            )

            return (categoryDto to payee)
        }

        fun getImported(connection: Connection, accountId: String): Pair<CategoryDto, AccountTree> {
            accountIdMapping[accountId]?.let { return it }

            // we need to walk backwards until we hit an imported node - or null
            // then we need to import forward

            fun getParent(accountId: String): AccountTree? =
                categoriesList.firstOrNull { accountId in it.subAccounts.map { it.id } }

            val path = LinkedList<AccountTree>()
            var toBeImported: AccountTree? = categoriesList.first { it.id == accountId }
            while (toBeImported != null) {
                path.addFirst(toBeImported)
                toBeImported = getParent(toBeImported.id)
            }

            var previous: CategoryDto? = null
            path.forEach { accountTree ->
                if (accountTree.imported) {
                    previous = accountIdMapping[accountTree.id]?.first
                    return@forEach
                }

                accountTree.imported = true

                val category = CategoryDto(
                    id = UUID.nameUUIDFromBytes(accountTree.id.toByteArray()).toString(),
                    name = accountTree.name,
                    description = null,
                    parentCategoryId = previous?.id,
                    ledgerId = ledger.id,
                )
                accountIdMapping[accountTree.id] = category to accountTree

                repository.addOrReplaceCategory(
                    connection,
                    userId,
                    category
                )
                previous = category
            }

            return accountIdMapping[accountId]!!
        }

    }

    fun import(
        userId: String,
        xml: String,
        ledgerId: String,
        bankAccountsToImport: List<String>,
        defaultPayeeName: String,
        payeeParent: CategoryDto,
        banknameToImporterFunctionMapper: (bankName: String) -> SupportedImporters?,
    ) {
        val ledger = dataSource.readTx { repository.getLedger(it, ledgerId) }

        val document = parseXMl(xml)

        val kMyMoneyRoot = children(document.documentElement).map { parseStructure(it) }

        val categoryImporter = parseAccounts(
            kMyMoneyRoot,
            userId,
            ledger,
            payeeParent,
        )

        // import all bank accounts and banks
        val bankAccounts = importBanks(
            ledger,
            bankAccountsToImport,
            kMyMoneyRoot,
            categoryImporter,
            userId,
            banknameToImporterFunctionMapper
        )

        val transactions = parseTransactions(kMyMoneyRoot)

        // import transactions and create bookings
        importTransactions(
            ledger,
            categoryImporter,
            bankAccounts,
            transactions,
            userId,
            defaultPayeeName
        )
    }

    private fun parseAccounts(
        kMyMoneyRoot: List<XmlElement>,
        userId: String,
        ledger: LedgerDto,
        payeeParent: CategoryDto,
    ): CategoryImporter {

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
                it.number,
                accountChildren(it.id),
                false,
                it
            )
        }

        val accountTree = accounts.filter { it.parentId == null }.map {
            AccountTree(
                it.id,
                it.type,
                it.opened,
                it.name,
                it.description,
                it.number,
                accountChildren(it.id),
                false,
                it
            )
        }

        return CategoryImporter(
            accountTree,
            repository,
            userId,
            ledger,
            kMyMoneyRoot.first { it.name == "PAYEES" }.children.map {
                Payee(
                    it.attributes["id"]!!,
                    it.attributes["name"]!!,
                )
            },
            payeeParent
        )
    }

    data class BankAccountWrapper(
        val bankAccountDto: BankAccountDto,
        val categoryDto: CategoryDto,
        val account: AccountTree,
    )

    private fun importTransactions(
        ledger: LedgerDto,
        categoryImporter: CategoryImporter,
        bankAndAccounts: BankAccounts,
        allTransactions: List<KTransaction>,
        userId: String,
        defaultPayeeName: String
    ) {
        bankAndAccounts.accountsForThisLedger.forEach { (bAccount, bAccountCategory, bAccountOriginal) ->

            dataSource.writeTx { conn ->

                // already imported?
                if (repository.getBankAccount(conn, ledger.id, bAccount.id)!!.transactionsCounter > 0) return@writeTx

                allTransactions
                    .filter { it.splits.any { it.accountId == bAccountOriginal.id } }
                    .forEach { tx ->
                        val mainTx = tx.splits.single { it.accountId == bAccountOriginal.id }
                        val targetAccounts = tx.splits.filterNot { it.id == mainTx.id }

                        val transferOtherBankAccount = bankAndAccounts
                            .accountsForThisLedger
                            .firstOrNull { (_, otherAccount) -> otherAccount.id == targetAccounts.first().accountId }

                        val payeesOrAccounts = targetAccounts
                            .mapNotNull { a ->

                                if (a.payeeId != null) {
                                    categoryImporter.getImportedPayee(conn, a.payeeId).first
                                } else {
                                    if (a.accountId in bankAndAccounts.otherLedgerAccountIds) {
                                        return@mapNotNull null
                                    }
                                    categoryImporter.getImported(conn, a.accountId).first
                                }

                            }
                            .joinToString(",") { it.name }
                            .ifBlank { null }

                        val memos = targetAccounts
                            .mapNotNull { it.memo }
                            .ifEmpty { null }
                            ?.joinToString(",")

                        // bank-tx
                        val bankTxId = repository.addBankTransaction(
                            conn,
                            userId,
                            BankTransactionAdd(
                                listOfNotNull(payeesOrAccounts, memos).joinToString(separator = ": "),
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
                            repository.matchBankTransaction(
                                conn,
                                userId,
                                bAccount.id,
                                bankTxId,
                                bAccount.ledgerId,
                                alreadyBookedCandidate.id,
                            )
                        } else {
                            val bookingId = repository.addBooking(
                                conn,
                                userId,
                                BookingAdd(
                                    bAccount.ledgerId,
                                    transferOtherBankAccount?.let { tx.id },
                                    tx.postDate.atStartOfDay().atZone(zoneId).toInstant(),
                                    listOf(
                                        BookingRecordAdd(
                                            mainTx.memo,
                                            when {
                                                transferOtherBankAccount != null -> bAccountCategory.id
                                                else -> (mainTx.payeeId?.let {
                                                    categoryImporter.getImportedPayee(
                                                        conn,
                                                        it
                                                    )
                                                }
                                                    ?: categoryImporter.getImportedPayeeByName(conn, defaultPayeeName))
                                                    .first
                                                    .id
                                            },
                                            mainTx.amountInCents.toLong() * -1,
                                        )
                                    ) + targetAccounts.map {
                                        BookingRecordAdd(
                                            it.memo,
                                            run {
                                                if (it.accountId in bankAndAccounts.otherLedgerAccountIds) {
                                                    if (it.payeeId != null) {
                                                        categoryImporter.getImportedPayee(conn, it.payeeId).first
                                                    } else {
                                                        error("Do not know which category to make the booking against")
                                                    }
                                                } else {
                                                    categoryImporter.getImported(conn, it.accountId).first
                                                }
                                            }.id,
                                            it.amountInCents.toLong() * -1,
                                        )
                                    }
                                )
                            )
                            repository.matchBankTransaction(
                                connection = conn,
                                userId = userId,
                                bankAccountId = bAccount.id,
                                transactionId = bankTxId,
                                ledgerId = bAccount.ledgerId,
                                bookingId = bookingId
                            )
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

    data class BankAccounts(
        val accountsForThisLedger: List<BankAccountWrapper>,
        val otherLedgerAccountIds: List<String>,
    )

    private fun importBanks(
        ledger: LedgerDto,
        bankAccountsToImport: List<String>,
        kMyMoneyRoot: List<XmlElement>,
        categoryImporter: CategoryImporter,
        userId: String,
        banknameToImporterFunctionMapper: (bankName: String) -> SupportedImporters?,
    ): BankAccounts {


        val banksAndAccounts = kMyMoneyRoot
            .first { it.name == "INSTITUTIONS" }
            .children
            .map { institution ->

                val allAccountIds = institution
                    .children
                    .firstOrNull { it.name == "ACCOUNTIDS" }
                    ?.children
                    ?.map { it.attributes["id"]!! }
                    ?: emptyList()

                KBank(
                    institution.attributes["id"]!!,
                    institution.attributes["name"]!!,
                    allAccountIds
                )
            }.filter { it.accountIds.isNotEmpty() }

        // import Banks
        return dataSource.writeTx { conn ->

            val result = mutableListOf<BankAccountWrapper>()
            val otherBankAccountIds = mutableListOf<String>()

            banksAndAccounts.forEach { kBank ->

                val accountsToBeImported = kBank.accountIds
                    .filter { categoryImporter.getAccountWithoutImporting(it).name in bankAccountsToImport }
                otherBankAccountIds.addAll(
                    kBank.accountIds.filterNot { it in accountsToBeImported }
                )

                if (accountsToBeImported.isEmpty()) return@forEach

                val bankId = UUID.nameUUIDFromBytes(kBank.id.toByteArray()).toString()
                repository.addOrReplaceBank(
                    conn, userId, BankDto(
                        bankId,
                        kBank.name,
                        null,
                        banknameToImporterFunctionMapper(kBank.name)?.name,
                    )
                )

                result.addAll(
                    accountsToBeImported.map { accountId ->

                        // import category related to bank account
                        val (categoryDto: CategoryDto, account: AccountTree) = categoryImporter.getImported(
                            conn,
                            accountId
                        )

                        // deduce bankAccountId from category, which is based on accountId
                        val bankAccountId = UUID.nameUUIDFromBytes(categoryDto.id.toByteArray()).toString()

                        val bankAccountDto = repository.getBankAccount(conn, ledger.id, bankAccountId) ?: run {
                            val bankAccount = BankAccountDto(
                                bankAccountId,
                                categoryDto.name,
                                null,
                                ledger.id,
                                bankId,
                                account.number ?: "unknown",
                                account.opened!!,
                                if (account.original.closed) LocalDate.now() else null,
                                categoryDto.id,
                                0,
                                0,
                                0,
                                0,
                            )
                            repository.addBankAccount(conn, userId, bankAccount)
                            bankAccount
                        }


                        BankAccountWrapper(
                            bankAccountDto,
                            categoryDto,
                            account
                        )
                    }
                )

            }

            BankAccounts(
                result,
                otherBankAccountIds
            )
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
        val accountIds: List<String>,
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
        val number: String?,
        val subAccounts: List<AccountTree>,
        var imported: Boolean,
        val original: Account,
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