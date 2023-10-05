package com.dehnes.accounting.services

import com.dehnes.accounting.database.AccessRequest
import com.dehnes.accounting.database.CategoryFilter
import com.dehnes.accounting.database.Repository
import com.dehnes.accounting.database.Transactions.writeTx
import javax.sql.DataSource

class BookingWriteService(
    private val repository: Repository,
    private val dataSource: DataSource,
    private val bookingReadService: BookingReadService,
    private val categoryReadService: CategoryReadService,
) {

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
             * A) find all booking my sourceCategoryId and change to destinationCategoryId
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
                            conn,
                            userId,
                            ledgerId,
                            b.id,
                            br.id,
                            destinationCategoryId
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