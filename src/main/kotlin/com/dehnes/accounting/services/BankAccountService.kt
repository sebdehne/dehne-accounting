package com.dehnes.accounting.services

import com.dehnes.accounting.database.*
import com.dehnes.accounting.database.Transactions.readTx
import com.dehnes.accounting.domain.InformationElement
import java.sql.Connection
import java.time.Instant
import javax.sql.DataSource

class BankAccountService(
    private val bookingRepository: BookingRepository,
    private val bankRepository: BankRepository,
    private val bankAccountRepository: BankAccountRepository,
    private val dataSource: DataSource,
    private val authorizationService: AuthorizationService,
    private val unbookedTransactionRepository: UnbookedTransactionRepository,
    private val changelog: Changelog,
) {

    fun getBankAccount(userId: String, realmId: String, accountId: String) =
        dataSource.readTx { conn ->
            authorizationService.assertAuthorization(conn, userId, realmId, AccessRequest.admin)
            val bankAccountDto =
                bankAccountRepository.getAllBankAccounts(conn, realmId).first { it.accountId == accountId }
            BankAccount(
                bankAccountDto.accountId,
                bankAccountDto.bankId,
                bankAccountDto.accountNumber,
                bankAccountDto.openDate,
                bankAccountDto.closeDate,
            )
        }

    fun deleteBankAccount(userId: String, realmId: String, accountId: String) {
        changelog.writeTx { conn ->
            authorizationService.assertAuthorization(conn, userId, realmId, AccessRequest.admin)
            bankAccountRepository.deleteBankAccount(conn, accountId)
        }
    }

    fun createOrUpdateBankAccount(userId: String, realmId: String, bankAccount: BankAccount) {
        changelog.writeTx { conn ->
            authorizationService.assertAuthorization(conn, userId, realmId, AccessRequest.admin)

            val existing = bankAccountRepository.getAllBankAccounts(conn, realmId)
                .firstOrNull { it.accountId == bankAccount.accountId }
            if (existing != null) {
                bankAccountRepository.updateBankAccount(conn, bankAccount)
            } else {
                bankAccountRepository.insertBankAccount(conn, bankAccount)
            }
        }
    }


    fun deleteAllUnbookedTransactions(userId: String, realmId: String, accountId: String) {
        changelog.writeTx { conn ->
            authorizationService.assertAuthorization(conn, userId, realmId, AccessRequest.write)

            unbookedTransactionRepository.deleteAll(conn, accountId)
        }
    }

    fun getOverview(userId: String, realmId: String) = dataSource.readTx { conn ->
        authorizationService.assertAuthorization(conn, userId, realmId, AccessRequest.read)
        getOverview(conn, realmId)
    }

    private fun getOverview(conn: Connection, realmId: String): List<BankWithAccounts> {
        val allBanks = bankRepository.getAll(conn)

        val allBankAccounts = bankAccountRepository.getAllBankAccounts(conn, realmId)

        return allBanks.mapNotNull { bank ->

            val accounts = allBankAccounts.filter { it.bankId == bank.id }
            if (accounts.isEmpty()) return@mapNotNull null

            BankWithAccounts(
                bank.id,
                bank.name,
                bank.description,
                accounts.map { ba ->
                    val lastKnownUnbookedTransactionDate =
                        unbookedTransactionRepository.getLastKnownUnbookedTransactionDate(
                            conn,
                            ba.accountId
                        )

                    val lastKnownBookingDate = bookingRepository.getLastKnownBookingDate(
                        ba.accountId,
                        realmId
                    )


                    BankAccountView(
                        ba.accountId,
                        ba.accountNumber,
                        ba.openDate,
                        ba.closeDate,
                        bookingRepository.getSum(
                            ba.accountId,
                            realmId,
                            DateRangeFilter()
                        ) + unbookedTransactionRepository.getSum(
                            conn,
                            ba.accountId,
                            BankTxDateRangeFilter()
                        ),
                        listOfNotNull(
                            lastKnownBookingDate,
                            lastKnownUnbookedTransactionDate
                        ).maxOrNull()
                    )
                },
            )
        }
    }

    fun getBankAccountTransactions(
        userId: String,
        realmId: String,
        accountId: String,
        dateRangeFilter: DateRangeFilter
    ) = dataSource.readTx { conn ->

        authorizationService.assertAuthorization(conn, userId, realmId, AccessRequest.read)

        getBankAccountTransactions(
            conn,
            realmId,
            accountId,
            dateRangeFilter
        )
    }

    fun getBankAccountTransactions(
        conn: Connection,
        realmId: String,
        accountId: String,
        dateRangeFilter: DateRangeFilter
    ): List<BankAccountTransaction> {

        val bookingRecords = bookingRepository.getBookings(
            realmId,
            Int.MAX_VALUE,
            listOf(
                dateRangeFilter,
                AccountIdFilter(
                    accountId = accountId,
                    realmId = realmId
                )
            )
        ).map {
            val mainEntry = it.entries.single { it.accountId == accountId }
            val otherEntry = it.entries.first { it.accountId != accountId }

            BankAccountTransaction(
                BookingReference(
                    it.id,
                    mainEntry.id,
                    otherEntry.accountId
                ),
                null,
                it.datetime,
                it.description ?: mainEntry.description,
                mainEntry.amountInCents,
                0
            )
        }

        val unbookedTransactions = unbookedTransactionRepository.getUnbookedTransactions(
            conn,
            realmId,
            accountId,
            dateRangeFilter
        ).map {


            BankAccountTransaction(
                null,
                UnbookedReference(
                    it.id,
                    it.otherAccountNumber
                ),
                it.datetime,
                it.memo,
                it.amountInCents,
                0
            )
        }

        var balance = bookingRepository.getSum(
            accountId,
            realmId,
            DateRangeFilter(toExclusive = dateRangeFilter.from)
        )

        balance += unbookedTransactionRepository.getSum(
            conn,
            accountId,
            BankTxDateRangeFilter(toExclusive = dateRangeFilter.from)
        )

        return (bookingRecords + unbookedTransactions).sortedWith(
            compareBy(
                { it.datetime },
                { it.bookingReference?.bookingId },
                { it.unbookedReference?.unbookedId }
            )
        ).map {
            balance += it.amountInCents

            it.copy(balance = balance)
        }.reversed()
    }

    fun deleteUnbookedTransaction(
        userId: String,
        realmId: String,
        accountId: String,
        deleteUnbookedBankTransactionId: Long
    ) {
        changelog.writeTx { conn ->
            authorizationService.assertAuthorization(
                conn,
                userId,
                realmId,
                AccessRequest.write
            )

            unbookedTransactionRepository.delete(
                conn,
                accountId,
                deleteUnbookedBankTransactionId
            )
        }
    }



}

data class BankWithAccounts(
    override val id: String,
    override val name: String,
    override val description: String?,
    val accounts: List<BankAccountView>,
) : InformationElement()

data class BankAccountView(
    val accountId: String,
    val accountNumber: String?,
    val openDate: Instant,
    val closeDate: Instant?,
    val balance: Long,
    val lastKnownTransactionDate: Instant?
)

data class BankAccountTransaction(
    val bookingReference: BookingReference?,
    val unbookedReference: UnbookedReference?,
    val datetime: Instant,
    val memo: String?,
    val amountInCents: Long,
    val balance: Long,
)

data class BookingReference(
    val bookingId: Long,
    val bookingEntryId: Long,
    val otherAccountId: String,
)

data class UnbookedReference(
    val unbookedId: Long,
    val otherAccountNumber: String?,
)

