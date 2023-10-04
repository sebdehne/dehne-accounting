package com.dehnes.accounting.services

import com.dehnes.accounting.api.dtos.LedgerView
import com.dehnes.accounting.database.BookingRecordView
import com.dehnes.accounting.database.BookingView
import com.dehnes.accounting.database.BookingsFilter
import com.dehnes.accounting.database.Repository
import com.dehnes.accounting.database.Transactions.readTx
import java.sql.Connection
import javax.sql.DataSource
import kotlin.math.sign

class BookingReadService(
    private val repository: Repository,
    private val dataSource: DataSource,
    private val userService: UserService,
    private val categoryService: CategoryService,
) {

    fun getBookings(
        userId: String,
        ledgerId: String,
        limit: Int,
        vararg filters: BookingsFilter?,
    ) = dataSource.readTx { conn ->

        val ledger = getLedgerAuthorized(conn, userId, ledgerId, write = false)

        repository.getBookings(
            conn,
            categoryService.get(conn),
            ledger.id,
            limit,
            *filters
        )
    }

    fun listLedgers(userId: String, write: Boolean): List<LedgerView> {
        return dataSource.readTx {
            listLedgers(it, userId, write)
        }
    }

    fun getLedgerAuthorized(
        connection: Connection,
        userId: String,
        ledgerId: String,
        write: Boolean,
    ): LedgerView {
        val ledger = listLedgers(connection, userId, write).firstOrNull { it.id == ledgerId }
            ?: error("User $userId has not access to $ledgerId")

        return ledger
    }


    fun listLedgers(connection: Connection, userId: String, write: Boolean): List<LedgerView> {
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
                l.bookingsCounter,
                a.accessLevel
            )
        }.filter { user.isAdmin || it.accessLevel.hasAccess(write) }
    }

}


enum class RootCategory(
    val id: String,
) {
    Asset("2f131f9d-c47e-4c9d-998b-8c477913fdcc"),
    Liability("68b9ab02-67ac-4a09-b080-b043c1a4cde6"),
    Expense("0c0ff3a5-d0c3-48fa-a53b-a71b200d0c96"),
    Income("71795865-c7ce-4e09-ba30-56520a5266dd"),
    Equity("688979ce-986b-4b23-8f7c-62b271172398"),
    Parties("12ffcb90-f019-47fe-bdb0-27da28c2d745"),
}

enum class BookingType {
    payment,
    income,
    transfer,
    internalBooking,
    ;

    companion object {
        fun determineTime(records: List<BookingRecordView>, categories: Categories): BookingType {
            val rootsAndDirections = records.map { r ->
                val root = categories.findRoot(r.category.id).toRootType()
                root to r.amount.sign
            }.filterNot { it.second == 0 }

            val accounts = listOf(RootCategory.Asset, RootCategory.Liability)
            val bookings = listOf(RootCategory.Income, RootCategory.Expense)
            return when {
                rootsAndDirections.all { it.first in accounts } -> transfer
                rootsAndDirections.all { it.first in bookings } -> internalBooking
                rootsAndDirections.any { it.first == RootCategory.Parties && it.second == -1 } -> income
                rootsAndDirections.any { it.first == RootCategory.Parties && it.second == 1 } -> payment
                else -> error("Unknown booking type")
            }
        }
    }
}

