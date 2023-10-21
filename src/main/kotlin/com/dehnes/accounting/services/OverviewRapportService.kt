package com.dehnes.accounting.services

import com.dehnes.accounting.database.AccountDto
import com.dehnes.accounting.database.AccountsRepository
import com.dehnes.accounting.database.BookingRepository
import com.dehnes.accounting.database.DateRangeFilter
import com.dehnes.accounting.database.Transactions.readTx
import com.dehnes.accounting.domain.StandardAccount
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
                listOf(
                    DateRangeFilter(
                        Instant.MIN,
                        rangeFilter.from
                    )
                )
            )
            val thisPeriodBookings = bookingRepository.getBookings(
                conn,
                realmId,
                Int.MAX_VALUE,
                listOf(
                    rangeFilter
                )
            )

            fun getForAccount(a: AccountDto): OverviewRapportAccount {
                val children = allAccounts
                    .filter { it.parentAccountId == a.id }
                    .map { getForAccount(it) }
                    .filter { it.hasValue() }

                val openingBalance = openingBalanceBookings.flatMap { booking ->
                    booking.entries.filter { it.accountId == a.id }
                }.sumOf { it.amountInCents } + children.sumOf { it.openBalance }

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
                }

                val thisPeriod = entries.sumOf { it.amountInCents } + children.sumOf { it.thisPeriod }

                return OverviewRapportAccount(
                    a.id,
                    a.name,
                    openingBalance,
                    thisPeriod,
                    openingBalance + thisPeriod,
                    children,
                    entries
                )
            }


            StandardAccount.entries
                .filter { it.parent == null }
                .map { root ->
                    allAccounts.first { it.name == root.name && it.parentAccountId == null }
                }
                .map { getForAccount(it) }
                .filter { it.hasValue() }

        }

}


data class OverviewRapportAccount(
    val accountId: String,
    val name: String,
    val openBalance: Long,
    val thisPeriod: Long,
    val closeBalance: Long,
    val children: List<OverviewRapportAccount>,
    val records: List<OverviewRapportEntry>,
) {
    fun hasValue() = thisPeriod != 0L || openBalance != 0L
}

data class OverviewRapportEntry(
    val bookingId: Long,
    val bookingEntryId: Long,
    val bookingDescription: String?,
    val bookingEntryDescription: String?,
    val datetime: Instant,
    val amountInCents: Long,
)