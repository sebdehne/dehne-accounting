package com.dehnes.accounting.services

import com.dehnes.accounting.database.Repository
import com.dehnes.accounting.database.Transactions.writeTx
import javax.sql.DataSource

class BookingWriteService(
    private val repository: Repository,
    private val dataSource: DataSource,
    private val bookingReadService: BookingReadService,
) {

    fun removeLast(
        userId: String,
        ledgerId: String,
        bookingId: Long,
    ) {

        dataSource.writeTx { conn ->
            val ledger = bookingReadService.getLedgerAuthorized(
                conn,
                userId,
                ledgerId,
                write = true
            )

            repository.removeBooking(
                conn,
                userId,
                ledger.id,
                bookingId
            )
        }

    }
}