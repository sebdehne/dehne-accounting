package com.dehnes.accounting.services

import com.dehnes.accounting.database.*
import com.dehnes.accounting.database.Transactions.readTx
import javax.sql.DataSource

class SimpleRapportService(
    private val dataSource: DataSource,
    private val bookingRepository: BookingRepository,
    private val accountsRepository: AccountsRepository,
) {

    fun createUnauthorizedRapport(realmId: String, filter: DateRangeFilter): List<AccountWithBookings> {
        return dataSource.readTx { conn ->
            val allBookings = bookingRepository.getBookings(conn, realmId, Int.MAX_VALUE, filter)
            val allAccounts = accountsRepository.getAll(conn, realmId)

            fun get(a: AccountDto): AccountWithBookings {
                val children = allAccounts.filter { it.parentAccountId == a.id }
                val bookings = allBookings.flatMap { b ->
                    b.entries.mapNotNull { e ->
                        if (e.accountId == a.id) b to e else null
                    }
                }

                val entries = bookings.map {
                    BookingEntryReference(
                        it.second,
                        it.first
                    )
                }
                val childrenAccounts = children.map { get(it) }.filter { it.hasSomethingToShow() }
                return AccountWithBookings(
                    a,
                    entries,
                    entries.sumOf { it.bookingEntry.amountInCents } + childrenAccounts.sumOf { it.total },
                    childrenAccounts
                )
            }

            allAccounts
                .filter { it.parentAccountId == null }
                .map { get(it) }
                .filter { it.hasSomethingToShow() }
        }
    }
}

data class AccountWithBookings(
    val account: AccountDto,
    val entries: List<BookingEntryReference>,
    val total: Long,
    val childrenAccounts: List<AccountWithBookings>,
) {
    fun hasSomethingToShow(): Boolean {
        return entries.isNotEmpty() || childrenAccounts.any { it.hasSomethingToShow() }
    }
}

data class BookingEntryReference(
    val bookingEntry: BookingEntry,
    val booking: Booking,
)
