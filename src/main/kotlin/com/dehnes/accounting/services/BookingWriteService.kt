package com.dehnes.accounting.services

import com.dehnes.accounting.database.*
import com.dehnes.accounting.database.Transactions.writeTx
import javax.sql.DataSource

class BookingWriteService(
    private val repository: Repository,
    private val dataSource: DataSource,
    private val bookingReadService: BookingReadService,
    private val categoryReadService: CategoryReadService,
) {

    fun addOrReplaceBooking(
        userId: String,
        bookingView: BookingView
    ) {
        dataSource.writeTx { conn ->
            bookingReadService.getLedgerAuthorized(conn, userId, bookingView.ledgerId, AccessRequest.write)

            val existing = repository.getBookings(
                conn,
                bookingView.ledgerId,
                Int.MAX_VALUE,
                SingleBookingFilter(bookingId = bookingView.id)
            ).singleOrNull()

            if (existing != null) {
                repository.editBooking(
                    conn,
                    userId,
                    bookingView
                )
            } else {
                repository.addBooking(
                    conn,
                    userId,
                    bookingView
                )
            }
        }
    }

    fun mergeCategories(
        userId: String,
        ledgerId: String,
        sourceCategoryId: String,
        destinationCategoryId: String,
    ) {
        dataSource.writeTx { conn ->
            bookingReadService.getLedgerAuthorized(conn, userId, ledgerId, AccessRequest.owner)
            val categories = categoryReadService.get(
                conn,
                ledgerId
            )

            check(categories.asList.any { it.id == sourceCategoryId })
            check(categories.asList.any { it.id == destinationCategoryId })

            /*
             * A) find all booking-records my sourceCategoryId and change to destinationCategoryId
             */
            repository.getBookings(
                conn,
                ledgerId,
                Int.MAX_VALUE,
                CategoryFilter(listOf(sourceCategoryId))
            ).forEach { b ->
                b.records
                    .filter { br -> br.categoryId == sourceCategoryId }
                    .forEach { br ->
                        repository.changeCategory(
                            connection = conn,
                            userId = userId,
                            ledgerId = ledgerId,
                            bookingId = b.id,
                            bookingRecordId = br.id,
                            sourceCategoryId = sourceCategoryId,
                            destinationCategoryId = destinationCategoryId
                        )
                    }
            }

            /*
             * B) change parentId of all child-categories for sourceCategoryId to new parent: destinationCategoryId
             */
            categories.asList.filter { it.parentCategoryId == sourceCategoryId }.forEach { child ->
                repository.addOrReplaceCategory(
                    conn,
                    userId,
                    child.copy(parentCategoryId = destinationCategoryId)
                )
            }

            /*
             * Delete source category
             */
            repository.removeCategory(
                conn,
                userId,
                ledgerId,
                sourceCategoryId
            )
        }
    }

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
                AccessRequest.write
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