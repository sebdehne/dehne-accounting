package com.dehnes.accounting.services

import com.dehnes.accounting.database.Booking
import com.dehnes.accounting.database.BookingRepository
import com.dehnes.accounting.database.BookingsFilter
import com.dehnes.accounting.database.Transactions.readTx
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
}