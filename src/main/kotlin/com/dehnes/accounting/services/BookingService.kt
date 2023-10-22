package com.dehnes.accounting.services

import com.dehnes.accounting.database.*
import com.dehnes.accounting.database.Transactions.readTx
import com.dehnes.accounting.database.Transactions.writeTx
import javax.sql.DataSource

class BookingService(
    private val dataSource: DataSource,
    private val bookingRepository: BookingRepository,
    private val authorizationService: AuthorizationService,
) {

    fun getBookings(
        userId: String,
        realmId: String,
        bookingsFilters: List<BookingsFilter>,
    ): List<Booking> = dataSource.readTx { conn ->
        authorizationService.assertAuthorization(conn, userId, realmId, AccessRequest.read)

        bookingRepository.getBookings(
            conn,
            realmId,
            Int.MAX_VALUE,
            bookingsFilters
        )
    }

    fun getBooking(userId: String, realmId: String, bookingId: Long) = dataSource.readTx { conn ->
        authorizationService.assertAuthorization(conn, userId, realmId, AccessRequest.read)

        bookingRepository.getBookings(
            conn,
            realmId,
            Int.MAX_VALUE,
            listOf(SingleBookingFilter(bookingId))
        ).single()
    }

    fun createOrUpdateBooking(userId: String, realmId: String, booking: Booking) = dataSource.writeTx { conn ->
        authorizationService.assertAuthorization(
            conn,
            userId,
            realmId,
            AccessRequest.write
        )

        val existingBooking = bookingRepository.getBookings(
            conn,
            realmId,
            Int.MAX_VALUE,
            listOf(SingleBookingFilter(booking.id))
        ).singleOrNull()

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
                            amountInCents = it.amountInCents
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
        dataSource.writeTx { conn ->
            authorizationService.assertAuthorization(
                conn,
                userId,
                realmId,
                AccessRequest.write
            )

            bookingRepository.deleteBooking(
                conn,
                realmId,
                bookingId
            )
        }
    }
}