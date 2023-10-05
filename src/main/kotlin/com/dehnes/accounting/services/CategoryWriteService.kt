package com.dehnes.accounting.services

import com.dehnes.accounting.database.AccessRequest
import com.dehnes.accounting.database.CategoryDto
import com.dehnes.accounting.database.Repository
import com.dehnes.accounting.database.Transactions.writeTx
import javax.sql.DataSource

class CategoryWriteService(
    private val repository: Repository,
    private val dataSource: DataSource,
    private val bookingReadService: BookingReadService,
) {

    fun addOrReplaceCategory(userId: String, categoryDto: CategoryDto) {
        dataSource.writeTx { conn ->
            bookingReadService.getLedgerAuthorized(conn, userId, categoryDto.ledgerId, AccessRequest.owner)

            repository.addOrReplaceCategory(conn, userId, categoryDto)
        }
    }
}
