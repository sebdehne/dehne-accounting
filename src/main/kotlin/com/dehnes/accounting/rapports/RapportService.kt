package com.dehnes.accounting.rapports

import com.dehnes.accounting.database.BookingView
import com.dehnes.accounting.database.CategoryDto
import com.dehnes.accounting.database.DateRangeFilter
import com.dehnes.accounting.database.Repository
import com.dehnes.accounting.database.Transactions.readTx
import com.dehnes.accounting.services.CategoryLeaf
import com.dehnes.accounting.services.CategoryService
import java.sql.Connection
import java.time.Instant
import javax.sql.DataSource

class RapportService(
    private val repository: Repository,
    private val categoryService: CategoryService,
    private val dataSource: DataSource,
) {

    fun rapport(
        request: RapportRequest,
    ): List<RapportLeaf> = dataSource.readTx { rapport(it, request) }

    fun rapport(
        connection: Connection,
        request: RapportRequest,
    ): List<RapportLeaf> {

        val categories = categoryService.get(connection, request.ledgerId)

        val bookings = repository.getBookings(
            connection,
            request.ledgerId,
            Int.MAX_VALUE,
            DateRangeFilter(request.from, request.toExcluding)
        )

        // build the rapport tree somehow :)
        fun buildLeaf(categoryLeaf: CategoryLeaf, parentCategoryId: String?): RapportLeaf? {
            val mappedRecords = bookings
                .flatMap { b -> b.records.filter { it.categoryId == categoryLeaf.id }.map { b to it.id } }
                .map { (b, brId) -> RapportRecord(b, brId) }

            val children = categoryLeaf.children.mapNotNull { c ->
                buildLeaf(c, categoryLeaf.id)
            }

            if (children.isEmpty() && mappedRecords.isEmpty()) {
                return null
            }

            return RapportLeaf(
                CategoryDto(
                    categoryLeaf.id,
                    categoryLeaf.name,
                    categoryLeaf.description,
                    parentCategoryId,
                    request.ledgerId
                ),
                mappedRecords,
                mappedRecords.sumOf { it.amount() } + children.sumOf { it.totalAmountInCents },
                children.sortedBy { it.categoryDto.name }
            )
        }

        return categories.asTree.mapNotNull { buildLeaf(it, null) }
    }

}

data class RapportRequest(
    val ledgerId: String,
    val from: Instant,
    val toExcluding: Instant,
)

data class RapportLeaf(
    val categoryDto: CategoryDto,
    val records: List<RapportRecord>,
    val totalAmountInCents: Long,
    val children: List<RapportLeaf>,
)

data class RapportRecord(
    val booking: BookingView,
    val bookingRecordId: Long,
) {
    fun amount() = booking.records.first { it.id == bookingRecordId }.amount
    fun description() = booking.records.first { it.id == bookingRecordId }.description
}


