package com.dehnes.accounting.services

import com.dehnes.accounting.api.dtos.LedgerView
import com.dehnes.accounting.database.*
import java.sql.Connection

class BookingReadService(
    private val repository: Repository,
    private val userService: UserService,
) {

    fun getBooking(
        conn: Connection,
        ledgerId: String,
        bookingId: Long,
    ): BookingView = repository.getBookings(
        conn,
        ledgerId,
        Int.MAX_VALUE,
        SingleBookingFilter(bookingId)
    ).single()

    fun getBookings(
        conn: Connection,
        userId: String,
        ledgerId: String,
        limit: Int,
        vararg filters: BookingsFilter?,
    ): List<BookingView> {
        val ledger = getLedgerAuthorized(conn, userId, ledgerId, AccessRequest.read)

        return repository.getBookings(
            conn,
            ledger.id,
            limit,
            *filters
        )
    }


    fun getLedgerAuthorized(
        connection: Connection,
        userId: String,
        ledgerId: String,
        accessRequest: AccessRequest,
    ): LedgerView = listLedgers(connection, userId, accessRequest).firstOrNull { it.id == ledgerId }
        ?: error("User $userId has not access to $ledgerId")

    fun listLedgers(connection: Connection, userId: String, accessRequest: AccessRequest): List<LedgerView> {
        val user = userService.getUserById(connection, userId) ?: error("Unknown userId=$userId")
        if (!user.isActive) return emptyList()

        val allLedger = repository.getAllLedger(connection)
        val userLedgers = repository.getUserLedgers(connection).filter { it.userId == user.id }

        return allLedger.mapNotNull { l ->
            val a = userLedgers.firstOrNull { it.ledgerId == l.id } ?: return@mapNotNull null
            LedgerView(
                l.id,
                l.name,
                l.description,
                a.accessLevel
            )
        }.filter { user.isAdmin || it.accessLevel.hasAccess(accessRequest) }
    }

}


