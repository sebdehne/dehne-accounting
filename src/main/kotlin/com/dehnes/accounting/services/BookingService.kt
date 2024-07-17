package com.dehnes.accounting.services

import com.dehnes.accounting.api.dtos.ReadResponse
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
        rangeFilter: DateRangeFilter,
        accountId: String,
    ) = dataSource.readTx { conn ->

        authorizationService.assertAuthorization(
            conn,
            userId,
            realmId,
            AccessRequest.read,
        )


        val bookings = bookingRepository.getBookingsForRange(
            realmId,
            rangeFilter,
        ).filter { it.entries.any { it.accountId == accountId } }.toMutableList()

        val balance = bookingRepository.getSum(
            realmId = realmId,
            accountId = accountId,
            dateRangeFilter = DateRangeFilter(toExclusive = rangeFilter.from)
        ).first

        val unbookedTransactions = unbookedTransactionRepository.getUnbookedTransactions(
            conn,
            realmId,
            accountId,
            rangeFilter
        )
        val sumUnbooked = unbookedTransactionRepository.getSum(
            conn,
            accountId,
            BankTxDateRangeFilter(toExclusive = rangeFilter.from)
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

        ReadResponse(
            bookings = bookings.sortedWith(
                object : Comparator<Booking> {
                    override fun compare(o1: Booking, o2: Booking): Int {
                        if (o1.datetime != o2.datetime) {
                            return o2.datetime.compareTo(o1.datetime)
                        }
                        return o2.id.compareTo(o1.id)
                    }
                }
            ),
            bookingsBalance = balance + sumUnbooked
        )
    }

    fun getBooking(userId: String, realmId: String, bookingId: Long) = dataSource.readTx { conn ->
        authorizationService.assertAuthorization(
            conn,
            userId,
            realmId,
            AccessRequest.read,
        )

        bookingRepository.getBooking(
            realmId,
            bookingId
        )
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
        val existingBooking = bookingRepository.getBooking(
            realmId,
            booking.id
        )

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

            val booking = bookingRepository.getBooking(
                realmId,
                bookingId
            )

            if (booking != null) {
                authorizationService.assertAuthorization(
                    conn,
                    userId,
                    realmId,
                    AccessRequest.write,
                    booking.datetime
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