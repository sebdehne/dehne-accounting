package com.dehnes.accounting.services

import com.dehnes.accounting.database.*
import com.dehnes.accounting.database.Transactions.readTx
import com.dehnes.accounting.database.Transactions.writeTx
import com.dehnes.accounting.domain.InformationElement
import java.sql.Connection
import java.time.Instant
import javax.sql.DataSource

class BankAccountService(
    private val bookingRepository: BookingRepository,
    private val bankRepository: BankRepository,
    private val bankAccountRepository: BankAccountRepository,
    private val accountsRepository: AccountsRepository,
    private val dataSource: DataSource,
    private val authorizationService: AuthorizationService,
    private val unbookedTransactionRepository: UnbookedTransactionRepository,
) {

    fun deleteAllUnbookedTransactions(userId: String, realmId: String, accountId: String) {
        dataSource.writeTx { conn ->
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
        val allAccounts = accountsRepository.getAll(conn, realmId)

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
                        conn,
                        ba.accountId,
                        realmId
                    )


                    BankAccountView(
                        allAccounts.first { it.id == ba.accountId },
                        ba.accountNumber,
                        ba.openDate,
                        ba.closeDate,
                        bookingRepository.getSum(
                            conn,
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
                }
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
            conn,
            realmId,
            Int.MAX_VALUE,
            listOf(
                dateRangeFilter,
                AccountIdFilter(accountId, realmId)
            )
        ).map {
            val mainEntry = it.entries.single { it.accountId == accountId }
            val otherEntry = it.entries.single { it.accountId != accountId }

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
            conn,
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


}

data class BankWithAccounts(
    override val id: String,
    override val name: String,
    override val description: String?,
    val accounts: List<BankAccountView>,
) : InformationElement()

data class BankAccountView(
    val account: AccountDto,
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

