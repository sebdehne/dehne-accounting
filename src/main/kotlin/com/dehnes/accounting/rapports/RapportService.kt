package com.dehnes.accounting.rapports

import com.dehnes.accounting.services.RootCategory
import com.dehnes.accounting.services.CategoryLeaf
import com.dehnes.accounting.services.CategoryService
import com.dehnes.accounting.database.BookingView
import com.dehnes.accounting.database.CategoryDto
import com.dehnes.accounting.database.DateRangeFilter
import com.dehnes.accounting.database.Repository
import com.dehnes.accounting.database.Transactions.readTx
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

        val categories = categoryService.get(connection)
        val bookings = repository.getBookings(
            connection,
            categories,
            request.ledgerId,
            Int.MAX_VALUE,
            DateRangeFilter(request.from, request.toExcluding)
        )

        val allCategoryIds = categories.resolveAllChildIds(request.topCategoryIds.ifEmpty { RootCategory.entries.map { it.id } })

        // build the rapport tree somehow :)
        fun buildLeaf(categoryLeaf: CategoryLeaf, parentCategoryId: String?): RapportLeaf? {
            val mappedRecords = if (categoryLeaf.id in allCategoryIds) {
                bookings
                    .flatMap { b -> b.records.filter { it.category.id == categoryLeaf.id }.map { b to it.id } }
                    .map { (b, brId) -> RapportRecord(
                        b,
                        brId,
                    ) }
            } else emptyList()

            val children = categoryLeaf.children.mapNotNull { c ->
                buildLeaf(c, categoryLeaf.id)
            }

            if (children.isEmpty() && mappedRecords.isEmpty()) {
                return null
            }

            return RapportLeaf(
                CategoryDto(categoryLeaf.id, categoryLeaf.name, categoryLeaf.description, parentCategoryId),
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
    val topCategoryIds: List<String>,
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


