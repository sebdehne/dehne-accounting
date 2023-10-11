package com.dehnes.accounting.utils

import com.dehnes.accounting.bank.importers.SupportedImporters
import com.dehnes.accounting.database.*
import com.dehnes.accounting.database.Transactions.writeTx
import com.dehnes.accounting.domain.StandardAccount
import com.dehnes.accounting.kmymoney.KMyMoneyUtils
import com.dehnes.accounting.utils.DateTimeUtils.plusDays
import com.dehnes.accounting.utils.DateTimeUtils.zoneId
import com.dehnes.accounting.kmymoney.KMyMoneyUtils.getAllAccounts
import com.dehnes.accounting.kmymoney.KMyMoneyUtils.getAllPayees
import com.dehnes.accounting.kmymoney.KMyMoneyUtils.parseAllBanks
import com.dehnes.accounting.kmymoney.KMyMoneyUtils.parseTransactions
import com.dehnes.accounting.kmymoney.utils.XmlUtils
import com.dehnes.accounting.utils.StringUtils.deterministicId
import java.sql.Connection
import java.time.Instant
import java.time.LocalDate
import java.util.*
import javax.sql.DataSource

interface BookingCreator {
    fun createBooking(
        realmId: String,
        datetime: Instant,
        mainSplit: KMyMoneyUtils.KTransactionSplit,
        otherSplits: Map<KMyMoneyUtils.KTransactionSplit, KMyMoneyImporter2.AccountDtoWrapper>,
        bankAccount: BankAccount,
        bankAccountDto: AccountDto,
        accountImporter: AccountImporter,
        bookingRepository: BookingRepository,
        connection: Connection,
    ): List<AddBooking>
}

object OpeningBalanceCreator : BookingCreator {
    override fun createBooking(
        realmId: String,
        datetime: Instant,
        mainSplit: KMyMoneyUtils.KTransactionSplit,
        otherSplits: Map<KMyMoneyUtils.KTransactionSplit, KMyMoneyImporter2.AccountDtoWrapper>,
        bankAccount: BankAccount,
        bankAccountDto: AccountDto,
        accountImporter: AccountImporter,
        bookingRepository: BookingRepository,
        connection: Connection
    ): List<AddBooking> = listOf(
        AddBooking(
            realmId, null, datetime, listOf(
                AddBookingEntry(
                    null,
                    bankAccountDto.id,
                    mainSplit.amountInCents,
                ), AddBookingEntry(
                    null, StandardAccount.OpeningBalances.toAccountId(realmId), mainSplit.amountInCents * -1
                )
            )
        )
    )
}

object InternalBookingCreator : BookingCreator {
    override fun createBooking(
        realmId: String,
        datetime: Instant,
        mainSplit: KMyMoneyUtils.KTransactionSplit,
        otherSplits: Map<KMyMoneyUtils.KTransactionSplit, KMyMoneyImporter2.AccountDtoWrapper>,
        bankAccount: BankAccount,
        bankAccountDto: AccountDto,
        accountImporter: AccountImporter,
        bookingRepository: BookingRepository,
        connection: Connection
    ): List<AddBooking> = listOf(AddBooking(realmId, mainSplit.memo, datetime, otherSplits.entries.map {
        AddBookingEntry(
            null, it.value.accountDto.id, it.key.amountInCents
        )
    }))
}

object PaymentToParty : BookingCreator {
    override fun createBooking(
        realmId: String,
        datetime: Instant,
        mainSplit: KMyMoneyUtils.KTransactionSplit,
        otherSplits: Map<KMyMoneyUtils.KTransactionSplit, KMyMoneyImporter2.AccountDtoWrapper>,
        bankAccount: BankAccount,
        bankAccountDto: AccountDto,
        accountImporter: AccountImporter,
        bookingRepository: BookingRepository,
        connection: Connection
    ): List<AddBooking> {

        val accountPayable = accountImporter.getImportedAccountPayable(
            connection,
            mainSplit.payeeId
        ).accountDto

        return listOf(
            AddBooking(
                realmId,
                mainSplit.memo,
                datetime,
                listOf(
                    AddBookingEntry(
                        null, bankAccountDto.id, mainSplit.amountInCents
                    ),
                    AddBookingEntry(null, accountPayable.id, otherSplits.entries.sumOf { it.key.amountInCents }),
                )
            ),
            AddBooking(realmId, mainSplit.memo, datetime, listOf(
                AddBookingEntry(
                    null, accountPayable.id, mainSplit.amountInCents
                )
            ) + otherSplits.entries.map {
                AddBookingEntry(
                    null, it.value.accountDto.id, it.key.amountInCents
                )
            }),
        )
    }
}

object IncomeFromPartyCreator : BookingCreator {
    override fun createBooking(
        realmId: String,
        datetime: Instant,
        mainSplit: KMyMoneyUtils.KTransactionSplit,
        otherSplits: Map<KMyMoneyUtils.KTransactionSplit, KMyMoneyImporter2.AccountDtoWrapper>,
        bankAccount: BankAccount,
        bankAccountDto: AccountDto,
        accountImporter: AccountImporter,
        bookingRepository: BookingRepository,
        connection: Connection
    ): List<AddBooking> {

        val accountReceivable = accountImporter.getImportedAccountReceivable(
            connection,
            mainSplit.payeeId
        ).accountDto

        return listOf(
            AddBooking(
                realmId, mainSplit.memo, datetime, listOf(
                    AddBookingEntry(
                        null, bankAccountDto.id, mainSplit.amountInCents
                    ), AddBookingEntry(null, accountReceivable.id, otherSplits.entries.sumOf { it.key.amountInCents })
                )
            ),
            AddBooking(realmId, mainSplit.memo, datetime, listOf(
                AddBookingEntry(
                    null, accountReceivable.id, mainSplit.amountInCents
                )
            ) + otherSplits.entries.map {
                AddBookingEntry(
                    null, it.value.accountDto.id, it.key.amountInCents
                )
            })
        )
    }
}

object TransferInsideRealmCreator : BookingCreator {
    override fun createBooking(
        realmId: String,
        datetime: Instant,
        mainSplit: KMyMoneyUtils.KTransactionSplit,
        otherSplits: Map<KMyMoneyUtils.KTransactionSplit, KMyMoneyImporter2.AccountDtoWrapper>,
        bankAccount: BankAccount,
        bankAccountDto: AccountDto,
        accountImporter: AccountImporter,
        bookingRepository: BookingRepository,
        connection: Connection
    ): List<AddBooking> {

        val alreadyBooked = bookingRepository.getBookings(
            connection, realmId, Int.MAX_VALUE, DateRangeFilter(
                datetime, datetime.plusDays(1)
            )
        ).any { b ->
            if (b.entries.size != 2) return@any false

            val mainEntry = b.entries.firstOrNull { it.accountId == bankAccountDto.id } ?: return@any false
            val otherEntry = b.entries.firstOrNull { it.accountId != bankAccountDto.id } ?: run {
                TODO()
            }

            otherSplits.entries.single().value.accountDto.id == otherEntry.accountId && mainEntry.amountInCents == mainSplit.amountInCents
        }

        return if (alreadyBooked) {
            emptyList()
        } else {
            listOf(
                AddBooking(
                    realmId, mainSplit.memo, datetime, listOf(
                        AddBookingEntry(
                            null, bankAccountDto.id, mainSplit.amountInCents
                        ),
                        AddBookingEntry(
                            null, otherSplits.entries.single().value.accountDto.id, mainSplit.amountInCents * -1
                        ),
                    )
                )
            )
        }
    }
}

object TransferOutsideRealm : BookingCreator {
    override fun createBooking(
        realmId: String,
        datetime: Instant,
        mainSplit: KMyMoneyUtils.KTransactionSplit,
        otherSplits: Map<KMyMoneyUtils.KTransactionSplit, KMyMoneyImporter2.AccountDtoWrapper>,
        bankAccount: BankAccount,
        bankAccountDto: AccountDto,
        accountImporter: AccountImporter,
        bookingRepository: BookingRepository,
        connection: Connection
    ): List<AddBooking> {

        val intermediateAccount = if (mainSplit.amountInCents > 0) {
            accountImporter.getImportedAccountReceivable(connection, mainSplit.payeeId)
        } else {
            accountImporter.getImportedAccountPayable(connection, mainSplit.payeeId)
        }

        return listOf(
            AddBooking(
                realmId,
                mainSplit.memo,
                datetime,
                listOf(
                    AddBookingEntry(
                        null,
                        bankAccountDto.id,
                        mainSplit.amountInCents
                    ),
                    AddBookingEntry(
                        null,
                        intermediateAccount.accountDto.id,
                        mainSplit.amountInCents * -1
                    )
                )
            ),
            AddBooking(
                realmId,
                mainSplit.memo,
                datetime,
                listOf(
                    AddBookingEntry(
                        null,
                        intermediateAccount.accountDto.id,
                        mainSplit.amountInCents
                    ),
                    AddBookingEntry(
                        null,
                        StandardAccount.OtherBankTransfers.toAccountId(realmId),
                        mainSplit.amountInCents * -1
                    )
                )
            ),
        )
    }
}

enum class TransactionType(
    val bookingCreator: BookingCreator
) {
    openingBalance(OpeningBalanceCreator),
    internalBooking(InternalBookingCreator),
    paymentToParty(PaymentToParty),
    incomeFromParty(IncomeFromPartyCreator),
    transferOutsideRealm(TransferOutsideRealm),
    transferInsideRealm(TransferInsideRealmCreator),
}

class KMyMoneyImporter2(
    private val partyRepository: PartyRepository,
    private val bankRepository: BankRepository,
    private val bankAccountRepository: BankAccountRepository,
    private val accountsRepository: AccountsRepository,
    private val bookingRepository: BookingRepository,
    private val dataSource: DataSource,
) {

    fun import(
        xml: String,
        realmId: String,
        bankAccountNamesToImport: Map<String, StandardAccount>,
        banknameToImporterFunctionMapper: (bankName: String) -> SupportedImporters?,
        payeeNameToAccountsReceivableAccount: Map<String, List<String>>,
        defaultPayeeName: String,
        preImportTransform: (
            transaction: KMyMoneyUtils.KTransaction,
            kMyMoneyRoot: List<XmlUtils.XmlElement>,
            bankAccount: BankAccount,
            bankAccountDto: AccountDtoWrapper,
            accountImporter: AccountImporter,
        ) -> KMyMoneyUtils.KTransaction
    ) {

        val kMyMoneyRoot = XmlUtils.parseKMyMoneyFile(xml)

        val allAccounts = kMyMoneyRoot.getAllAccounts()

        val allBanks = kMyMoneyRoot.parseAllBanks()

        val allTransactions = kMyMoneyRoot.parseTransactions()

        val accountImporter = AccountImporter(
            allAccounts,
            realmId,
            accountsRepository,
            partyRepository,
            kMyMoneyRoot.getAllPayees(),
            payeeNameToAccountsReceivableAccount,
            defaultPayeeName,
        )

        val bankAccountImporter = BankAccountImporter(
            accountImporter,
            allBanks,
            allAccounts,
            bankRepository,
            bankAccountRepository,
            banknameToImporterFunctionMapper,
            realmId,
        )

        val bankAccountsToImport = dataSource.writeTx { conn ->
            allBanks.flatMap { kBank ->
                kBank.accountIds.mapNotNull { kAccountId ->
                    val kAccount = allAccounts.first { it.id == kAccountId }
                    if (kAccount.name in bankAccountNamesToImport) {
                        val (bankAccount, accountDto) = bankAccountImporter.getImportedBankAccount(
                            conn,
                            kAccount.id,
                            bankAccountNamesToImport[kAccount.name]!!
                        )
                        BankAccountWrapper(
                            kAccount.id,
                            bankAccount,
                            accountDto
                        )
                    } else null
                }
            }
        }

        bankAccountsToImport.forEach { (kAccountId, bankAccount, accountDto) ->

            dataSource.writeTx { conn ->


                val transactions = allTransactions.filter { it.splits.any { it.accountId == kAccountId } }

                transactions.forEachIndexed { index, kTransaction ->
                    importTransaction(
                        conn,
                        bankAccount,
                        accountDto,
                        accountImporter,
                        bookingRepository,
                        realmId,
                        bankAccountsToImport.map { it.kAccountId },
                        kTransaction,
                        kMyMoneyRoot.children,
                        preImportTransform
                    )
                }
            }
        }
    }

    data class BankAccountWrapper(
        val kAccountId: String, val bankAccount: BankAccount, val accountDto: AccountDtoWrapper
    )

    private fun importTransaction(
        conn: Connection,
        bankAccount: BankAccount,
        bankAccountDto: AccountDtoWrapper,
        accountImporter: AccountImporter,
        bookingRepository: BookingRepository,
        realmId: String,
        bankAccountIdsInsideRealm: List<String>,
        transactionIn: KMyMoneyUtils.KTransaction,
        kMyMoneyRoot: List<XmlUtils.XmlElement>,
        preImportTransform: (
            transaction: KMyMoneyUtils.KTransaction,
            kMyMoneyRoot: List<XmlUtils.XmlElement>,
            bankAccount: BankAccount,
            bankAccountDto: AccountDtoWrapper,
            accountImporter: AccountImporter,
        ) -> KMyMoneyUtils.KTransaction
    ) {

        val transaction = preImportTransform(
            transactionIn,
            kMyMoneyRoot,
            bankAccount,
            bankAccountDto,
            accountImporter
        )


        val mainSplit = transaction.splits.single {
            it.accountId.deterministicId(realmId) == bankAccountDto.accountDto.id
        }
        val otherSplits = transaction.splits
            .filterNot { it.accountId == mainSplit.accountId }
            .associateWith { accountImporter.getImported(conn, it.accountId) }
        val datetime = transaction.postDate.atStartOfDay().atZone(zoneId).toInstant()

        fun AccountDtoWrapper.isBankAccount(): Boolean =
            parentPath.last().id == StandardAccount.BankAccountAsset.toAccountId(realmId)
                    || parentPath.last().id == StandardAccount.BankAccountLiability.toAccountId(realmId)

        val isAgainstOtherBankAccount = otherSplits.entries.singleOrNull()?.value?.isBankAccount() ?: false

        val type = when {
            isAgainstOtherBankAccount
                    && otherSplits.all { it.key.accountId in bankAccountIdsInsideRealm } ->
                TransactionType.transferInsideRealm

            isAgainstOtherBankAccount
                    && otherSplits.all { it.key.accountId !in bankAccountIdsInsideRealm } ->
                TransactionType.transferOutsideRealm

            mainSplit.amountInCents > 0 && otherSplits.size == 1 && otherSplits.all {
                it.value.accountDto.id == StandardAccount.OpeningBalances.toAccountId(realmId)
            } -> TransactionType.openingBalance

            mainSplit.amountInCents == 0L -> TransactionType.internalBooking

            mainSplit.amountInCents < 0 -> TransactionType.paymentToParty

            else -> TransactionType.incomeFromParty
        }

        type.bookingCreator.createBooking(
            realmId,
            datetime,
            mainSplit,
            otherSplits,
            bankAccount,
            bankAccountDto.accountDto,
            accountImporter,
            bookingRepository,
            conn
        ).forEach { b ->
            bookingRepository.insert(conn, b)
        }
    }

    data class AccountDtoWrapper(
        val parentPath: List<AccountDto>,
        val accountDto: AccountDto,
    )

    internal class BankAccountImporter(
        private val accountImporter: AccountImporter,
        private val allBanks: List<KMyMoneyUtils.KBank>,
        private val allAccounts: List<KMyMoneyUtils.Account>,
        private val bankRepository: BankRepository,
        private val bankAccountRepository: BankAccountRepository,
        private val banknameToImporterFunctionMapper: (bankName: String) -> SupportedImporters?,
        private val realmId: String,
    ) {
        private val alreadyImported = mutableMapOf<String, Pair<BankAccount, AccountDtoWrapper>>()

        fun getImportedBankAccount(
            connection: Connection,
            kAccountId: String,
            parent: StandardAccount
        ) = alreadyImported.getOrPut(kAccountId) {

            val account = allAccounts.first { it.id == kAccountId }
            val kBank = allBanks.first { it.accountIds.any { it == kAccountId } }
            val bank = bankRepository.getByName(connection, kBank.name) ?: run {
                val bank = BankDto(
                    kBank.id.deterministicId(realmId),
                    kBank.name,
                    null,
                    banknameToImporterFunctionMapper(kBank.name)?.name
                )
                bankRepository.insert(connection, bank)
                bank
            }

            val accountDtoWrapper = accountImporter.getImported(connection, account.id, parent)

            val bankAccount = bankAccountRepository.insert(
                connection,
                accountDtoWrapper.accountDto,
                bank.id,
                null,
                (account.opened ?: LocalDate.now()).atStartOfDay().atZone(zoneId).toInstant(),
                null
            )

            bankAccount to accountDtoWrapper
        }

    }

    


}

class AccountImporter(
    private val allAccounts: List<KMyMoneyUtils.Account>,
    private val realmId: String,
    private val accountsRepository: AccountsRepository,
    private val partyRepository: PartyRepository,
    private val allPayees: List<KMyMoneyUtils.Payee>,
    private val payeeNameToAccountsReceivableAccount: Map<String, List<String>>,
    private val defaultPayeeName: String,
) {

    private val alreadyImported = mutableMapOf<String, KMyMoneyImporter2.AccountDtoWrapper>()
    private val accountsPayableAlreadyImported = mutableMapOf<String, KMyMoneyImporter2.AccountDtoWrapper>()
    private val accountsReceivableAlreadyImported = mutableMapOf<String, KMyMoneyImporter2.AccountDtoWrapper>()

    private val rootMappings = mapOf(
        "AStd::Asset" to StandardAccount.Asset,
        "AStd::Liability" to StandardAccount.Liability,
        "AStd::Equity" to StandardAccount.Equity,
        "AStd::Income" to StandardAccount.Income,
        "AStd::Expense" to StandardAccount.Expense,
    )

    fun getKAccountIdForAccountName(accountName: String, matchParentId: String? = null) =
        allAccounts.singleOrNull { it.name == accountName && (matchParentId == null || it.parentId == matchParentId) }?.id
            ?: run {
                println()
                error("")
            }

    fun getImported(connection: Connection, kAccountId: String, parentForBankAccount: StandardAccount? = null) =
        alreadyImported.getOrPut(kAccountId) {
            val thisAccount = allAccounts.first { it.id == kAccountId }

            val pathKAccounts = LinkedList<KMyMoneyUtils.Account>()
            var current: KMyMoneyUtils.Account? = thisAccount
            while (current != null) {
                pathKAccounts.addFirst(current)
                current = current.parentId?.let { kId -> allAccounts.first { it.id == kId } }
            }

            if (parentForBankAccount != null) {
                val accountDto = AccountDto(
                    thisAccount.id.deterministicId(realmId),
                    thisAccount.name,
                    null,
                    realmId,
                    parentForBankAccount.toAccountId(realmId),
                    null,
                )
                accountsRepository.insert(connection, accountDto)
                return@getOrPut KMyMoneyImporter2.AccountDtoWrapper(
                    listOfNotNull(
                        parentForBankAccount.parent?.toAccountDto(realmId),
                        parentForBankAccount.toAccountDto(realmId),
                    ),
                    accountDto
                )
            }

            if (payeeNameToAccountsReceivableAccount.any { it.value == pathKAccounts.map { it.name } }) {
                val payeeName =
                    payeeNameToAccountsReceivableAccount.entries.first { it.value == pathKAccounts.map { it.name } }.key
                val payee = allPayees.first { it.name == payeeName }
                return@getOrPut getImportedAccountReceivable(connection, payee.id)
            }

            // check if this acount counts as accountsPayable
            val path = LinkedList<AccountDto>()
            pathKAccounts.forEach { kAccount ->
                if (kAccount.id in alreadyImported) {
                    alreadyImported[kAccount.id]!!.accountDto.apply {
                        path.add(this)
                    }
                } else {
                    if (kAccount.id in rootMappings) {
                        path.add(rootMappings[kAccount.id]!!.toAccountDto(realmId))
                    } else if (thisAccount.name == "Opening Balances"
                        && path.lastOrNull()?.id == StandardAccount.Equity.toAccountId(realmId)
                    ) {
                        path.add(StandardAccount.OpeningBalances.toAccountDto(realmId))
                    } else {
                        path.add(
                            AccountDto(
                                kAccount.id.deterministicId(realmId),
                                kAccount.name,
                                null,
                                realmId,
                                path.lastOrNull()?.id,
                                null,
                            )
                        )
                        accountsRepository.insert(connection, path.last())
                    }

                    if (kAccount.id != thisAccount.id) {
                        alreadyImported[kAccount.id] = KMyMoneyImporter2.AccountDtoWrapper(
                            path.toList().dropLast(1),
                            path.last()
                        )
                    }
                }
            }

            KMyMoneyImporter2.AccountDtoWrapper(
                path.dropLast(1),
                path.last()
            )
        }

    fun getImportedAccountPayable(connection: Connection, payeeIdIn: String?): KMyMoneyImporter2.AccountDtoWrapper {
        val payeeId = payeeIdIn ?: allPayees.first { it.name == defaultPayeeName }.id
        return accountsPayableAlreadyImported.getOrPut(payeeId) {

            val payee = allPayees.first { it.id == payeeId }
            val party = partyRepository.get(connection, payee.id.deterministicId(realmId)) ?: run {
                val party = Party(
                    payee.id.deterministicId(realmId), payee.name, null, realmId
                )
                partyRepository.insert(connection, party)
                party
            }

            val accountDto = AccountDto(
                party.id.deterministicId(realmId, "AccountPayable"),
                party.name,
                null,
                realmId,
                StandardAccount.AccountPayable.toAccountId(realmId),
                party.id
            )

            accountsRepository.insert(connection, accountDto)

            val accountDtoWrapper = KMyMoneyImporter2.AccountDtoWrapper(
                listOf(
                    StandardAccount.AccountPayable.parent!!.toAccountDto(realmId),
                    StandardAccount.AccountPayable.toAccountDto(realmId),
                ), accountDto
            )
            accountDtoWrapper
        }
    }

    fun getImportedAccountReceivable(connection: Connection, payeeIdIn: String?): KMyMoneyImporter2.AccountDtoWrapper {
        val payeeId = payeeIdIn ?: allPayees.first { it.name == defaultPayeeName }.id
        return accountsReceivableAlreadyImported.getOrPut(payeeId) {

            val payee = allPayees.first { it.id == payeeId }
            val party = partyRepository.get(connection, payee.id.deterministicId(realmId)) ?: run {
                val party = Party(
                    payee.id.deterministicId(realmId), payee.name, null, realmId
                )
                partyRepository.insert(connection, party)
                party
            }

            val accountDto = AccountDto(
                party.id.deterministicId(realmId, "AccountReceivable"),
                party.name,
                null,
                realmId,
                StandardAccount.AccountReceivable.toAccountId(realmId),
                party.id
            )

            accountsRepository.insert(connection, accountDto)

            val accountDtoWrapper = KMyMoneyImporter2.AccountDtoWrapper(
                listOf(
                    StandardAccount.AccountReceivable.parent!!.toAccountDto(realmId),
                    StandardAccount.AccountReceivable.toAccountDto(realmId),
                ), accountDto
            )
            accountDtoWrapper
        }
    }

}