package com.dehnes.accounting.services

import com.dehnes.accounting.api.dtos.LedgerView
import com.dehnes.accounting.database.AccessRequest
import com.dehnes.accounting.database.BookingsFilter
import com.dehnes.accounting.database.Repository
import com.dehnes.accounting.database.Transactions.readTx
import java.sql.Connection
import javax.sql.DataSource

class BookingReadService(
    private val repository: Repository,
    private val dataSource: DataSource,
    private val userService: UserService,
) {

    fun getBookings(
        userId: String,
        ledgerId: String,
        limit: Int,
        vararg filters: BookingsFilter?,
    ) = dataSource.readTx { conn ->

        val ledger = getLedgerAuthorized(conn, userId, ledgerId, AccessRequest.read)

        repository.getBookings(
            conn,
            ledger.id,
            limit,
            *filters
        )
    }

    fun listLedgers(userId: String, accessRequest: AccessRequest): List<LedgerView> {
        return dataSource.readTx {
            listLedgers(it, userId, accessRequest)
        }
    }

    fun getLedgerAuthorized(
        connection: Connection,
        userId: String,
        ledgerId: String,
        accessRequest: AccessRequest,
    ): LedgerView {
        val ledger = listLedgers(connection, userId, accessRequest).firstOrNull { it.id == ledgerId }
            ?: error("User $userId has not access to $ledgerId")

        return ledger
    }

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


