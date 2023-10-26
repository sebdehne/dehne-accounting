package com.dehnes.accounting.database

import com.dehnes.accounting.api.AccountsChanged
import com.dehnes.accounting.api.BookingsChanged
import com.dehnes.accounting.utils.SqlUtils
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant

class BookingRepository(
    private val realmRepository: RealmRepository,
    private val changelog: Changelog,
) {

    fun getSum(conn: Connection, accountId: String, realmId: String, dateRangeFilter: DateRangeFilter): Long {
        val (wheres, whereParams) = dateRangeFilter.whereAndParams()
        return conn.prepareStatement(
            """
            SELECT 
                sum(be.amount_in_cents) 
            from 
                booking b, booking_entry be 
            where 
                b.id = be.booking_id
                AND be.account_id = ?
                AND b.realm_id = ?
                AND $wheres
        """.trimIndent()
        ).use { preparedStatement ->
            val params = mutableListOf<Any>()
            params.add(accountId)
            params.add(realmId)
            params.addAll(whereParams)

            SqlUtils.setSqlParams(preparedStatement, params)

            preparedStatement.executeQuery().use { rs ->
                check(rs.next())
                rs.getLong(1)
            }
        }
    }

    fun getLastKnownBookingDate(conn: Connection, accountId: String, realmId: String): Instant? = conn.prepareStatement(
        """
        SELECT 
            max(b.datetime) 
        from 
            booking b, booking_entry be 
        where 
            b.id = be.booking_id
            AND be.account_id = ?
            AND b.realm_id = ?
    """.trimIndent()
    ).use { preparedStatement ->
        preparedStatement.setString(1, accountId)
        preparedStatement.setString(2, realmId)
        preparedStatement.executeQuery().use { rs ->
            check(rs.next())
            rs.getTimestamp(1)?.toInstant()
        }
    }

    fun getBookings(
        connection: Connection,
        realmId: String,
        limit: Int,
        filters: List<BookingsFilter>,
    ): List<Booking> {
        check(limit >= 0)

        val allEntries = connection.prepareStatement("SELECT * FROM booking_entry where realm_id = ?").use { preparedStatement ->
            preparedStatement.setString(1, realmId)
            preparedStatement.executeQuery().use { rs ->
                val l = mutableListOf<BookingEntryRaw>()
                while(rs.next()) {
                    l.add(BookingEntryRaw(
                        rs.getLong("id"),
                        rs.getLong("booking_id"),
                        rs.getString("description"),
                        rs.getString("account_id"),
                        rs.getLong("amount_in_cents"),
                    ))
                }
                l
            }
        }.filter { e ->
            filters.firstOrNull { it is AccountIdFilter }?.let {
                e.accountId == (it as AccountIdFilter).accountId
            } ?: true
        }.groupBy { it.bookingId }

        val allBookings = connection.prepareStatement("SELECT * FROM booking where realm_id = ?").use { preparedStatement ->
            preparedStatement.setString(1, realmId)
            preparedStatement.executeQuery().use { rs ->
                val l = mutableListOf<Booking>()
                while(rs.next()) {
                    l.add(Booking(
                        realmId,
                        rs.getLong("id"),
                        rs.getString("description"),
                        rs.getTimestamp("datetime").toInstant(),
                        emptyList()
                    ))
                }
                l
            }
        }.filter {

            val dateFilter = filters.firstOrNull { it is DateRangeFilter } as? DateRangeFilter
            val idFilter = filters.firstOrNull { it is SingleBookingFilter } as? SingleBookingFilter

            if (dateFilter != null) {
                if (it.datetime !in dateFilter.from..<dateFilter.toExclusive) return@filter false
            }
            if (idFilter != null) {
                if (it.id != idFilter.bookingId) return@filter false
            }

            true
        }

        val unsorted = allBookings.map { b ->
            Booking(
                realmId,
                b.id,
                b.description,
                b.datetime,
                allEntries.getOrDefault(b.id, emptyList()).map {
                    BookingEntry(
                        it.id,
                        it.description,
                        it.accountId,
                        it.amountInCents
                    )
                }.sortedByDescending { it.id }
            )
        }

        return unsorted.sortedWith(compareBy(
            {it.datetime},
            {it.id},
        )).reversed()
    }


    fun insert(
        connection: Connection,
        addBooking: AddBooking,
    ): Long {
        check(addBooking.entries.sumOf { it.amountInCents } == 0L) { "Booking entries do not sum to zero" }
        check(addBooking.entries.isNotEmpty())

        val nextBookingId = realmRepository.getNextBookingId(connection, addBooking.realmId)

        connection.prepareStatement(
            """
            INSERT INTO booking (realm_id, id, description, datetime) VALUES (?,?,?,?)
        """.trimIndent()
        ).use { preparedStatement ->
            preparedStatement.setString(1, addBooking.realmId)
            preparedStatement.setLong(2, nextBookingId)
            preparedStatement.setString(3, addBooking.description?.ifBlank { null })
            preparedStatement.setTimestamp(4, Timestamp.from(addBooking.datetime))
            preparedStatement.executeUpdate()
        }

        addBooking.entries.forEachIndexed { index, bookingEntry ->
            insertEntry(
                connection = connection,
                realmId = addBooking.realmId,
                bookingId = nextBookingId,
                id = index,
                description = bookingEntry.description,
                accountId = bookingEntry.accountId,
                amountInCents = bookingEntry.amountInCents
            )
        }

        changelog.add(BookingsChanged)

        return nextBookingId
    }

    private fun insertEntry(
        connection: Connection,
        realmId: String,
        bookingId: Long,
        id: Int,
        description: String?,
        accountId: String,
        amountInCents: Long,
    ) {
        connection.prepareStatement(
            """
            INSERT INTO booking_entry (
                realm_id, 
                booking_id, 
                id, 
                description, 
                account_id, 
                amount_in_cents
            ) VALUES (?,?,?,?,?,?)
        """.trimIndent()
        ).use { preparedStatement ->
            preparedStatement.setString(1, realmId)
            preparedStatement.setLong(2, bookingId)
            preparedStatement.setInt(3, id)
            preparedStatement.setString(4, description?.ifBlank { null })
            preparedStatement.setString(5, accountId)
            preparedStatement.setLong(6, amountInCents)
            preparedStatement.executeUpdate()
        }
    }

    fun editBooking(
        connection: Connection,
        realmId: String,
        booking: Booking
    ) {
        check(realmId == booking.realmId)
        check(booking.entries.isNotEmpty())
        check(booking.entries.sumOf { it.amountInCents } == 0L) { "Booking entries do not sum to zero" }

        connection.prepareStatement("DELETE FROM booking_entry where booking_id = ? and realm_id = ?")
            .use { preparedStatement ->
                preparedStatement.setLong(1, booking.id)
                preparedStatement.setString(2, realmId)
                preparedStatement.executeUpdate()
            }

        booking.entries.forEachIndexed { index, bookingEntry ->
            insertEntry(
                connection,
                realmId,
                booking.id,
                index,
                bookingEntry.description,
                bookingEntry.accountId,
                bookingEntry.amountInCents,
            )
        }

        connection.prepareStatement("UPDATE booking set datetime = ?, description = ? WHERE id = ? and realm_id = ?")
            .use { preparedStatement ->
                preparedStatement.setTimestamp(1, Timestamp.from(booking.datetime))
                preparedStatement.setString(2, booking.description?.ifBlank { null })
                preparedStatement.setLong(3, booking.id)
                preparedStatement.setString(4, realmId)
                check(preparedStatement.executeUpdate() == 1)
            }

        changelog.add(BookingsChanged)
    }

    fun deleteBooking(conn: Connection, realmId: String, bookingId: Long) {
        conn.prepareStatement("DELETE FROM booking_entry where booking_id = ? and realm_id = ?").use { preparedStatement ->
            preparedStatement.setLong(1, bookingId)
            preparedStatement.setString(2, realmId)
            preparedStatement.executeUpdate()
        }
        conn.prepareStatement("DELETE FROM booking where id = ? AND realm_id = ?").use { preparedStatement ->
            preparedStatement.setLong(1, bookingId)
            preparedStatement.setString(2, realmId)
            preparedStatement.executeUpdate()
        }
        changelog.add(BookingsChanged)
    }

    fun mergeAccount(conn: Connection, realmId: String, sourceAccountId: String, targetAccountId: String) {
        conn.prepareStatement("UPDATE booking_entry SET account_id = ? where realm_id = ? and account_id = ?").use { preparedStatement ->
            preparedStatement.setString(1, targetAccountId)
            preparedStatement.setString(2, realmId)
            preparedStatement.setString(3, sourceAccountId)
            preparedStatement.executeUpdate()
        }

        changelog.add(BookingsChanged)
    }
}


data class Booking(
    val realmId: String,
    val id: Long,
    val description: String?,
    val datetime: Instant,
    val entries: List<BookingEntry>,
)

data class BookingEntry(
    val id: Long,
    val description: String?,
    val accountId: String,
    val amountInCents: Long,
)

data class BookingEntryRaw(
    val id: Long,
    val bookingId: Long,
    val description: String?,
    val accountId: String,
    val amountInCents: Long,
)

data class AddBooking(
    val realmId: String,
    val description: String?,
    val datetime: Instant,
    val entries: List<AddBookingEntry>,
)

data class AddBookingEntry(
    val description: String?,
    val accountId: String,
    val amountInCents: Long,
)

data class BookingBookingRecordV2(
    val realmId: String,
    val bookingId: Long,
    val bookingDescription: String?,
    val datetime: Instant,
    val bookingEntryId: Long,
    val bookingEntryDescription: String?,
    val accountId: String,
    val amountInCents: Long,
)
