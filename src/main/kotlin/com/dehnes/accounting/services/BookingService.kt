package com.dehnes.accounting.services

import com.dehnes.accounting.database.*
import com.dehnes.accounting.database.Transactions.readTx
import javax.sql.DataSource

class BookingService(
    private val dataSource: DataSource,
    private val bookingRepository: BookingRepository,
    private val authorizationService: AuthorizationService,
    private val unbookedTransactionRepository: UnbookedTransactionRepository,
    private val changelog: Changelog,
) {

    fun getBookings(
        userId: String,
        realmId: String,
        bookingsFilters: List<BookingsFilter>,
    ): List<Booking> = dataSource.readTx { conn ->
        authorizationService.assertAuthorization(
            conn,
            userId,
            realmId,
            AccessRequest.read,
        )

        val bookings = bookingRepository.getBookings(
            realmId,
            Int.MAX_VALUE,
            bookingsFilters
        ).toMutableList()

        if (bookingsFilters.any { it is AccountIdFilter } && bookingsFilters.any { it is DateRangeFilter }) {
            val accountId = bookingsFilters.filterIsInstance<AccountIdFilter>().single().accountId
            val rangeFilter = bookingsFilters.filterIsInstance<DateRangeFilter>().single()

            val unbookedTransactions = unbookedTransactionRepository.getUnbookedTransactions(
                conn,
                realmId,
                accountId,
                rangeFilter
            )

            unbookedTransactions.forEach {
                bookings.add(
                    Booking(
                        realmId,
                        it.id,
                        it.memo,
                        it.datetime,
                        emptyList(),
                        it.amountInCents
                    )
                )
            }
        }

        bookings.sortedWith(
            object : Comparator<Booking> {
                override fun compare(o1: Booking, o2: Booking): Int {
                    if (o1.datetime != o2.datetime) {
                        return o2.datetime.compareTo(o1.datetime)
                    }
                    return o2.id.compareTo(o1.id)
                }
            }
        )
    }

    fun getBooking(userId: String, realmId: String, bookingId: Long) = dataSource.readTx { conn ->
        authorizationService.assertAuthorization(
            conn,
            userId,
            realmId,
            AccessRequest.read,
        )

        bookingRepository.getBookings(
            realmId,
            Int.MAX_VALUE,
            listOf(SingleBookingFilter(bookingId))
        ).single()
    }

    fun updateChecked(
        realmId: String,
        userId: String,
        bookingId: Long,
        bookingEntryId: Long,
        checked: Boolean,
    ) {
        changelog.writeTx { conn ->
            authorizationService.assertAuthorization(
                connection = conn,
                userId = userId,
                realmId = realmId,
                accessRequest = AccessRequest.write,
            )

            bookingRepository.updateChecked(
                connection = conn,
                realmId = realmId,
                bookingId = bookingId,
                bookingEntryId = bookingEntryId,
                checked = checked
            )
        }
    }

    fun createOrUpdateBooking(userId: String, realmId: String, booking: Booking) = changelog.writeTx { conn ->
        val existingBooking = bookingRepository.getBookings(
            realmId,
            Int.MAX_VALUE,
            listOf(SingleBookingFilter(booking.id))
        ).singleOrNull()

        authorizationService.assertAuthorization(
            conn,
            userId,
            realmId,
            AccessRequest.write,
            existingBooking?.datetime,
            booking.datetime
        )

        if (existingBooking == null) {
            bookingRepository.insert(
                conn,
                AddBooking(
                    realmId = realmId,
                    description = booking.description,
                    datetime = booking.datetime,
                    entries = booking.entries.map {
                        AddBookingEntry(
                            description = it.description,
                            accountId = it.accountId,
                            amountInCents = it.amountInCents,
                            checked = it.checked,
                        )
                    }
                )
            )
        } else {
            bookingRepository.editBooking(
                conn,
                realmId,
                booking
            )
            booking.id
        }
    }

    fun deleteBooking(userId: String, realmId: String, bookingId: Long) {
        changelog.writeTx { conn ->

            val bookings = bookingRepository.getBookings(
                realmId,
                1,
                listOf(SingleBookingFilter(bookingId))
            )

            if (bookings.isNotEmpty()) {
                authorizationService.assertAuthorization(
                    conn,
                    userId,
                    realmId,
                    AccessRequest.write,
                    bookings.single().datetime
                )

                bookingRepository.deleteBooking(
                    conn,
                    realmId,
                    bookingId
                )
            }
        }
    }
}