package com.dehnes.accounting.services

import com.dehnes.accounting.database.AccountDto
import com.dehnes.accounting.database.AccountsRepository
import com.dehnes.accounting.database.BookingRepository
import com.dehnes.accounting.database.DateRangeFilter
import com.dehnes.accounting.database.Transactions.readTx
import com.dehnes.accounting.domain.StandardAccount
import java.time.Duration
import java.time.Instant
import javax.sql.DataSource


class OverviewRapportService(
    private val dataSource: DataSource,
    private val bookingRepository: BookingRepository,
    private val accountsRepository: AccountsRepository,
) {

    fun createRapport(realmId: String, rangeFilter: DateRangeFilter) =
        dataSource.readTx { conn ->
            val allAccounts = accountsRepository.getAll(conn, realmId)

            val openingBalanceBookings = bookingRepository.getBookings(
                conn,
                realmId,
                Int.MAX_VALUE,
                listOf(DateRangeFilter(toExclusive = rangeFilter.from))
            )

            val thisPeriodBookings = bookingRepository.getBookings(
                conn,
                realmId,
                Int.MAX_VALUE,
                listOf(rangeFilter)
            )

            fun getForAccount(a: AccountDto): OverviewRapportAccount {
                val children = allAccounts
                    .filter { it.parentAccountId == a.id }
                    .map { getForAccount(it) }

                val openingBalance = openingBalanceBookings
                    .flatMap { it.entries.filter { it.accountId == a.id } }
                    .sumOf { it.amountInCents } + children.sumOf { it.openBalance }

                val entries = thisPeriodBookings.flatMap { booking ->
                    booking.entries.filter { it.accountId == a.id }.map { entry ->
                        OverviewRapportEntry(
                            booking.id,
                            entry.id,
                            booking.description,
                            entry.description,
                            booking.datetime,
                            entry.amountInCents
                        )
                    }
                } // where is account-payable in the rapport???

                val thisPeriod = entries.sumOf { it.amountInCents } + children.sumOf { it.thisPeriod }

                return OverviewRapportAccount(
                    a.id,
                    a.name,
                    openingBalance,
                    thisPeriod,
                    openingBalance + thisPeriod,
                    children,
                    children.sumOf { it.deepEntrySize } + entries.size
                )
            }

            StandardAccount.entries
                .filter { it.parent == null }
                .map { root -> allAccounts.first { it.id == root.toAccountId(realmId) } }
                .map { getForAccount(it) }
        }

}


data class OverviewRapportAccount(
    val accountId: String,
    val name: String,
    val openBalance: Long,
    val thisPeriod: Long,
    val closeBalance: Long,
    val children: List<OverviewRapportAccount>,
    val deepEntrySize: Int,
)

data class OverviewRapportEntry(
    val bookingId: Long,
    val bookingEntryId: Long,
    val bookingDescription: String?,
    val bookingEntryDescription: String?,
    val datetime: Instant,
    val amountInCents: Long,
)