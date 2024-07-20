package com.dehnes.accounting.database

import com.dehnes.accounting.api.BookingsChanged
import com.dehnes.accounting.database.Transactions.readTx
import com.dehnes.accounting.utils.Metrics.logTimed
import java.awt.print.Book
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource


class BookingRepository(
    private val realmRepository: RealmRepository,
    private val changelog: Changelog,
    private val dataSource: DataSource,
) {

    fun start() {
        refreshCache()
    }

    // realmId -> BookingId -> Booking
    private var cache: Map<String, MutableMap<Long, Booking>>? = null

    private fun getBookings(realmId: String): List<Booking> {
        if (cache == null) {
            refreshCache()
        }
        return (cache!![realmId] ?: error("RealmId=$realmId not found")).values.toList()
    }

    private fun addOrUpdateCache(booking: Booking) {
        if (cache == null) {
            refreshCache()
        }
        cache!![booking.realmId]!![booking.id] = booking
    }

    private fun removeBooking(realmId: String, bookingId: Long) {
        if (cache == null) {
            refreshCache()
        }
        cache!![realmId]!!.remove(bookingId)
    }

    private fun refreshCache() {
        logTimed("refreshCache") {
            dataSource.readTx { c ->
                val map = mutableMapOf<String, MutableMap<Long, MutableList<BookingEntry>>>()

                c.prepareStatement("SELECT * FROM booking_entry").use { preparedStatement ->
                    preparedStatement.executeQuery().use { rs ->
                        while (rs.next()) {
                            val m1 = map.getOrPut(rs.getString("realm_id")) { mutableMapOf() }
                            val m2 = m1.getOrPut(rs.getLong("booking_id")) { mutableListOf() }
                            m2.add(
                                BookingEntry(
                                    rs.getLong("id"),
                                    rs.getString("description"),
                                    rs.getString("account_id"),
                                    rs.getLong("amount_in_cents"),
                                    rs.getInt("checked") > 0,
                                )
                            )
                        }
                    }
                }

                val bookingsMap = mutableMapOf<String, MutableMap<Long, Booking>>()

                c.prepareStatement("SELECT * FROM booking").use { preparedStatement ->
                    preparedStatement.executeQuery().use { rs ->
                        while (rs.next()) {
                            val m = bookingsMap.getOrPut(
                                rs.getString("realm_id")
                            ) { mutableMapOf() }
                            m[rs.getLong("id")] = Booking(
                                rs.getString("realm_id"),
                                rs.getLong("id"),
                                rs.getString("description"),
                                rs.getTimestamp("datetime").toInstant(),
                                map[rs.getString("realm_id")]!!.getOrDefault(rs.getLong("id"), emptyList()),
                                0
                            )
                        }
                    }
                }

                cache = bookingsMap
            }
        }
    }

    fun getSum(
        realmId: String,
        accountId: String,
        dateRangeFilter: DateRangeFilter = DateRangeFilter()
    ) = getBookings(realmId)
        .filter { dateRangeFilter.contains(it.datetime) }
        .flatMap { it.entries }
        .filter { it.accountId == accountId }
        .map { it.amountInCents }
        .let {
            it.sum() to it.size
        }

    fun getLastKnownBookingDate(accountId: String, realmId: String) = getBookings(realmId)
        .filter { it.entries.any { it.accountId == accountId } }
        .maxByOrNull { it.datetime }
        ?.datetime

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

    fun getAllBookings(realmId: String) = getBookings(realmId)

    fun getBooking(realmId: String, id: Long) = getBookings(realmId).firstOrNull { it.id == id }

    fun getBookingsForRange(
        realmId: String,
        dateRangeFilter: DateRangeFilter,
    ) = getBookings(realmId).filter { dateRangeFilter.contains(it.datetime) }

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

        addOrUpdateCache(
            Booking(
                addBooking.realmId,
                nextBookingId,
                addBooking.description,
                addBooking.datetime,
                addBooking.entries.mapIndexed { index, e ->
                    BookingEntry(
                        index.toLong(),
                        e.description,
                        e.accountId,
                        e.amountInCents,
                        false
                    )
                },
                0
            )
        )

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

        updateCache(realmId, bookingId) {
            it?.copy(
                entries = it.entries.map {
                    if (it.id == bookingEntryId) {
                        it.copy(checked = checked)
                    } else {
                        it
                    }
                }
            )
        }

        changelog.add(BookingsChanged(realmId))

    }

    private fun updateCache(realmId: String, bookingId: Long, fn: (existing: Booking?) -> Booking?) {
        if (cache == null) {
            refreshCache()
        }
        val mutableMap = cache!!.get(realmId) ?: error("Realm not found $realmId")
        fn(mutableMap.getOrDefault(bookingId, null))?.let { b ->
            mutableMap[b.id] = b
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

        addOrUpdateCache(booking)

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

        removeBooking(realmId, bookingId)
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

        refreshCache()
        changelog.add(BookingsChanged(realmId))
    }
}


data class Booking(
    val realmId: String,
    val id: Long,
    val description: String?,
    val datetime: Instant,
    val entries: List<BookingEntry>,
    val unbookedAmountInCents: Long?,
)

data class BookingEntry(
    val id: Long,
    val description: String?,
    val accountId: String,
    val amountInCents: Long,
    val checked: Boolean,
)

data class BookingEntryRaw(
    val id: Long,
    val bookingId: Long,
    val description: String?,
    val accountId: String,
    val amountInCents: Long,
    val checked: Boolean,
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
    val checked: Boolean,
)

