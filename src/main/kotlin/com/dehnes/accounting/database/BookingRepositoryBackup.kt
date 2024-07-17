package com.dehnes.accounting.database

import com.dehnes.accounting.api.BookingsChanged
import com.dehnes.accounting.database.Transactions.readTx
import com.dehnes.accounting.utils.Metrics.logTimed
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource


class BookingRepositoryBackup(
    private val realmRepository: RealmRepository,
    private val changelog: Changelog,
    private val dataSource: DataSource,
) {

    // realmId -> BookingId -> Booking
    private var cache = mapOf<String, MutableMap<Long, Booking>>()

    init {
        refreshCache()
    }

    private fun refreshCache() {
        logTimed("refreshCache") {
            dataSource.readTx { c ->
                cache = realmRepository.getAll(c).associate {
                    it.id to getAllBookings(c, it.id).associateBy { it.id }.toMutableMap()
                }
            }
        }
    }

    fun getSum(
        realmId: String,
        accountId: String,
        dateRangeFilter: DateRangeFilter = DateRangeFilter()
    ): Pair<Long, Int> {
        val bookings = cache[realmId] ?: error("realm not found")
        return bookings
            .filter { dateRangeFilter.contains(it.value.datetime) }
            .flatMap { it.value.entries }
            .filter { it.accountId == accountId }
            .map { it.amountInCents }
            .let {
                it.sum() to it.size
            }
    }

    fun getLastKnownBookingDate(connection: Connection, accountId: String, realmId: String) =
        connection.prepareStatement(
            """
            SELECT 
                datetime 
            from 
                booking b
                left join booking_entry be on b.id = be.booking_id
            WHERE
                b.realm_id = ? AND be.account_id = ?
            ORDER BY b.datetime DESC
            LIMIT 1
        """.trimIndent()
        ).use { preparedStatement ->
            preparedStatement.setString(1, realmId)
            preparedStatement.setString(2, accountId)
            preparedStatement.executeQuery().use { rs ->
                if (rs.next()) {
                    rs.getTimestamp(1).toInstant()
                } else null
            }
        }

    private val bookingsBaseQuery = """
        select
            b.id as booking_id,
            be.id as booking_entry_id,
            b.description as booking_description,
            be.description as booking_entry_description,
            b.datetime,
            be.account_id,
            be.amount_in_cents,
            be.checked
        from booking b
            left join booking_entry be on be.booking_id = b.id
        where b.realm_id = ?
    """.trimIndent()

    private fun readBookings(realmId: String) = { rs: ResultSet ->
        val r = mutableListOf<BookingEntryRaw>()
        while (rs.next()) {
            r.add(
                BookingEntryRaw(
                    rs.getLong("booking_id"),
                    rs.getLong("booking_entry_id"),
                    rs.getString("booking_description"),
                    rs.getString("booking_entry_description"),
                    rs.getTimestamp("datetime").toInstant(),
                    rs.getString("account_id"),
                    rs.getLong("amount_in_cents"),
                    rs.getInt("checked") > 0
                )
            )
        }
        r.groupBy { it.bookingId }.map {
            Booking(
                realmId,
                it.key,
                it.value.first().bookingDescription,
                it.value.first().datetime,
                it.value.map {
                    BookingEntry(
                        it.bookingEntryId,
                        it.bookingEntryDescription,
                        it.accountId,
                        it.amountInCents,
                        it.checked
                    )
                },
                0
            )
        }
    }

    data class BookingEntryRaw(
        val bookingId: Long,
        val bookingEntryId: Long,
        val bookingDescription: String?,
        val bookingEntryDescription: String?,
        val datetime: Instant,
        val accountId: String,
        val amountInCents: Long,
        val checked: Boolean,
    )

    fun getAllBookings(connection: Connection, realmId: String) =
        connection.prepareStatement(bookingsBaseQuery).use { preparedStatement ->
            preparedStatement.setString(1, realmId)

            preparedStatement.executeQuery().use(readBookings(realmId))
        }

    fun getBooking(connection: Connection, realmId: String, id: Long) =
        connection.prepareStatement("$bookingsBaseQuery AND b.id = ?").use { preparedStatement ->
            preparedStatement.setString(1, realmId)
            preparedStatement.setLong(2, id)

            preparedStatement.executeQuery().use(readBookings(realmId))
        }.singleOrNull()

    fun getBookingsForRange(
        connection: Connection,
        realmId: String,
        dateRangeFilter: DateRangeFilter,
        accountIdFilter: AccountIdFilter?
    ) = connection.prepareStatement("$bookingsBaseQuery AND b.datetime >= ? AND b.datetime < ?")
        .use { preparedStatement ->
            preparedStatement.setString(1, realmId)
            preparedStatement.setTimestamp(2, Timestamp.from(dateRangeFilter.from))
            preparedStatement.setTimestamp(3, Timestamp.from(dateRangeFilter.toExclusive))
            preparedStatement.executeQuery().use(readBookings(realmId))
        }.filter { b ->
            if (accountIdFilter == null) {
                true
            } else {
                b.entries.any { it.accountId == accountIdFilter.accountId }
            }
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
                amountInCents = bookingEntry.amountInCents,
                checked = bookingEntry.checked,
            )
        }

        changelog.add(BookingsChanged(addBooking.realmId))

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
        checked: Boolean,
    ) {
        connection.prepareStatement(
            """
            INSERT INTO booking_entry (
                realm_id, 
                booking_id, 
                id, 
                description, 
                account_id, 
                amount_in_cents,
                checked
            ) VALUES (?,?,?,?,?,?,?)
        """.trimIndent()
        ).use { preparedStatement ->
            preparedStatement.setString(1, realmId)
            preparedStatement.setLong(2, bookingId)
            preparedStatement.setInt(3, id)
            preparedStatement.setString(4, description?.ifBlank { null })
            preparedStatement.setString(5, accountId)
            preparedStatement.setLong(6, amountInCents)
            preparedStatement.setInt(7, if (checked) 1 else 0)
            preparedStatement.executeUpdate()
        }
    }

    fun updateChecked(
        connection: Connection,
        realmId: String,
        bookingId: Long,
        bookingEntryId: Long,
        checked: Boolean,
    ) {
        connection.prepareStatement("UPDATE booking_entry SET checked = ? WHERE realm_id = ? AND booking_id = ? AND id = ?")
            .use { preparedStatement ->
                preparedStatement.setInt(1, if (checked) 1 else 0)
                preparedStatement.setString(2, realmId)
                preparedStatement.setLong(3, bookingId)
                preparedStatement.setLong(4, bookingEntryId)
                preparedStatement.executeUpdate()
            }

        changelog.add(BookingsChanged(realmId))

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
                bookingEntry.checked,
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

        changelog.add(BookingsChanged(realmId))
    }

    fun deleteBooking(conn: Connection, realmId: String, bookingId: Long) {
        conn.prepareStatement("DELETE FROM booking_entry where booking_id = ? and realm_id = ?")
            .use { preparedStatement ->
                preparedStatement.setLong(1, bookingId)
                preparedStatement.setString(2, realmId)
                preparedStatement.executeUpdate()
            }
        conn.prepareStatement("DELETE FROM booking where id = ? AND realm_id = ?").use { preparedStatement ->
            preparedStatement.setLong(1, bookingId)
            preparedStatement.setString(2, realmId)
            preparedStatement.executeUpdate()
        }
        changelog.add(BookingsChanged(realmId))
    }

    fun mergeAccount(conn: Connection, realmId: String, sourceAccountId: String, targetAccountId: String) {
        conn.prepareStatement("UPDATE booking_entry SET account_id = ? where realm_id = ? and account_id = ?")
            .use { preparedStatement ->
                preparedStatement.setString(1, targetAccountId)
                preparedStatement.setString(2, realmId)
                preparedStatement.setString(3, sourceAccountId)
                preparedStatement.executeUpdate()
            }

        changelog.add(BookingsChanged(realmId))
    }
}


