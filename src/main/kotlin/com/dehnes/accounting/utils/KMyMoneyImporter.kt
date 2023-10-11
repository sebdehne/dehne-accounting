package com.dehnes.accounting.utils

import com.dehnes.accounting.bank.importers.SupportedImporters
import com.dehnes.accounting.database.*
import com.dehnes.accounting.database.Transactions.readTx
import com.dehnes.accounting.database.Transactions.writeTx
import com.dehnes.accounting.kmymoney.KMyMoneyUtils
import com.dehnes.accounting.utils.DateTimeUtils.zoneId
import com.dehnes.accounting.utils.KMyMoneyImporter.TransactionType.*
import com.dehnes.accounting.kmymoney.KMyMoneyUtils.getAllAccounts
import com.dehnes.accounting.kmymoney.KMyMoneyUtils.getAllPayees
import com.dehnes.accounting.kmymoney.KMyMoneyUtils.parseAllBanks
import com.dehnes.accounting.kmymoney.KMyMoneyUtils.parseTransactions
import com.dehnes.accounting.kmymoney.utils.XmlUtils
import com.dehnes.accounting.kmymoney.utils.XmlUtils.parseKMyMoneyFile
import java.sql.Connection
import java.time.LocalDate
import java.util.*
import javax.sql.DataSource

class KMyMoneyImporter(
    private val repository: Repository,
    private val dataSource: DataSource,
) {

    internal class CategoryImporter(
        categories: List<AccountTree>,
        private val repository: Repository,
        private val userId: String,
        private val ledger: LedgerDto,
        private val payees: List<KMyMoneyUtils.Payee>,
        private val payeeParent: CategoryDto,
    ) {

        private val accountIdMapping: MutableMap<String, Pair<CategoryDto, AccountTree>> = mutableMapOf()
        private val payeeIdMapping: MutableMap<String, Pair<CategoryDto, KMyMoneyUtils.Payee>> = mutableMapOf()

        private fun asList(categories: List<AccountTree>) = categories.flatMap { asList(categories, it) }
        private fun asList(categories: List<AccountTree>, a: AccountTree): List<AccountTree> =
            a.subAccounts.flatMap { asList(categories, it) } + a

        private val categoriesList = asList(categories)

        fun getAccountWithoutImporting(accountId: String): AccountTree {
            return categoriesList.first { it.id == accountId }
        }

        fun getImportedPayee(
            connection: Connection,
            payeeId: String?,
            defaultPayeeName: String
        ): Pair<CategoryDto, KMyMoneyUtils.Payee> {
            val finalPayeeId = payeeId ?: payees.single { it.name == defaultPayeeName }.id
            payeeIdMapping[finalPayeeId]?.let { return it }

            val payee = payees.single { it.id == finalPayeeId }

            val categoryDto = CategoryDto(
                UUID.nameUUIDFromBytes(payee.id.toByteArray()).toString(),
                payee.name,
                null,
                payeeParent.id,
                ledger.id
            )

            payeeIdMapping[finalPayeeId] = (categoryDto to payee)

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

        val kMyMoneyRoot = parseKMyMoneyFile(xml)

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

        val transactions = kMyMoneyRoot.parseTransactions()

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
        kMyMoneyRoot: XmlUtils.XmlElement,
        userId: String,
        ledger: LedgerDto,
        payeeParent: CategoryDto,
    ): CategoryImporter {

        // Import all accounts (Categories)
        val accounts = kMyMoneyRoot.getAllAccounts()

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
            kMyMoneyRoot.getAllPayees(),
            payeeParent
        )
    }

    data class BankAccountWrapper(
        val bankAccountDto: BankAccountDto,
        val categoryDto: CategoryDto,
        val account: AccountTree,
    )

    enum class TransactionType(val multiplier: Int) {
        paymentOrIncome(-1),
        transferInsideLedger(1),
        transferOutsideLedger(-1),
    }

    private fun importTransactions(
        ledger: LedgerDto,
        categoryImporter: CategoryImporter,
        bankAndAccounts: BankAccounts,
        allTransactions: List<KMyMoneyUtils.KTransaction>,
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

                        val transferOtherBankAccountThisLedger = bankAndAccounts
                            .accountsForThisLedger
                            .firstOrNull { (_, _, account) -> account.id == targetAccounts.first().accountId }
                        val transferOtherBankAccountOtherLedger = bankAndAccounts
                            .otherLedgerAccountIds
                            .any { otherAccountId -> otherAccountId == targetAccounts.first().accountId }

                        val txType = when {
                            transferOtherBankAccountThisLedger == null && !transferOtherBankAccountOtherLedger -> paymentOrIncome
                            transferOtherBankAccountOtherLedger -> transferOutsideLedger
                            else -> transferInsideLedger
                        }

                        val payeesOrAccounts = targetAccounts
                            .mapNotNull { a ->

                                if (a.payeeId != null) {
                                    categoryImporter.getImportedPayee(conn, a.payeeId, defaultPayeeName).first
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
                                mainTx.amountInCents,
                            )
                        )

                        val alreadyBookedCandidate = if (txType == transferInsideLedger) {
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
                                connection = conn,
                                userId = userId,
                                bankAccountId = bAccount.id,
                                transactionId = bankTxId,
                                ledgerId = bAccount.ledgerId,
                                bookingId = alreadyBookedCandidate.id,
                            )
                        } else {
                            val mainRecord = BookingRecordView(
                                ledger.id,
                                0,
                                0,
                                mainTx.memo,
                                when (txType) {
                                    paymentOrIncome,
                                    transferOutsideLedger -> categoryImporter.getImportedPayee(
                                        conn,
                                        mainTx.payeeId,
                                        defaultPayeeName
                                    ).first.id

                                    transferInsideLedger -> bAccountCategory.id
                                },
                                mainTx.amountInCents * txType.multiplier,
                            )
                            val otherRecords = targetAccounts.map {
                                BookingRecordView(
                                    ledger.id,
                                    0,
                                    0,
                                    it.memo,
                                    when (txType) {
                                        transferInsideLedger,
                                        paymentOrIncome -> categoryImporter.getImported(conn, it.accountId).first.id

                                        transferOutsideLedger -> bAccountCategory.id
                                    },
                                    it.amountInCents * txType.multiplier,
                                )
                            }
                            val bookingId = repository.addBooking(
                                conn,
                                userId,
                                BookingView(
                                    bAccount.ledgerId,
                                    0,
                                    if (txType == transferInsideLedger) tx.id else null, // so that we can find a match
                                    tx.postDate.atStartOfDay().atZone(zoneId).toInstant(),
                                    listOf(
                                        mainRecord
                                    ) + otherRecords
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

    data class BankAccounts(
        val accountsForThisLedger: List<BankAccountWrapper>,
        val otherLedgerAccountIds: List<String>,
    )

    private fun importBanks(
        ledger: LedgerDto,
        bankAccountsToImport: List<String>,
        kMyMoneyRoot: XmlUtils.XmlElement,
        categoryImporter: CategoryImporter,
        userId: String,
        banknameToImporterFunctionMapper: (bankName: String) -> SupportedImporters?,
    ): BankAccounts {

        val banksAndAccounts = kMyMoneyRoot.parseAllBanks()

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





    data class AccountTree(
        val id: String,
        val type: String,
        val opened: LocalDate?,
        val name: String,
        val description: String?,
        val number: String?,
        val subAccounts: List<AccountTree>,
        var imported: Boolean,
        val original: KMyMoneyUtils.Account,
    )


}
