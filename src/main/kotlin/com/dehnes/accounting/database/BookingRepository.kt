package com.dehnes.accounting.database

import com.dehnes.accounting.utils.SqlUtils
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant

class BookingRepository(
    private val realmRepository: RealmRepository
) {

    fun getBookings(
        connection: Connection,
        realmId: String,
        limit: Int,
        vararg filters: BookingsFilter?,
    ): List<Booking> {
        check(limit >= 0)

        val params = mutableListOf<Any>()
        params.add(realmId)

        val filterData = filters.filterNotNull().map { it.whereAndParams() }
        val wheres = filterData.map { it.first }.joinToString(" AND ") { "($it)" }
        filterData.map { it.second }.forEach { params.addAll(it) }

        val records = connection.prepareStatement(
            """
            SELECT 
                b.id, 
                b.description, 
                b.datetime, 
                be.id beId, 
                be.description beDescription, 
                be.account_id, 
                be.amount_in_cents 
            FROM 
                booking b
            LEFT JOIN 
                booking_entry be on b.id = be.booking_id AND b.realm_id = be.realm_id
            WHERE
                b.realm_id = ? ${if (wheres.isNotBlank()) "AND $wheres" else ""}
            ORDER BY b.datetime, b.id , beId
            LIMIT $limit
        """.trimIndent()
        ).use { preparedStatement ->
            SqlUtils.setSqlParams(preparedStatement, params)

            preparedStatement.executeQuery().use { rs ->
                val l = mutableListOf<BookingBookingRecordV2>()
                while (rs.next()) {
                    l.add(
                        BookingBookingRecordV2(
                            realmId,
                            rs.getLong("id"),
                            rs.getString("description"),
                            rs.getTimestamp("datetime").toInstant(),
                            rs.getLong("beId"),
                            rs.getString("beDescription"),
                            rs.getString("account_id"),
                            rs.getLong("amount_in_cents"),
                        )
                    )
                }
                l
            }
        }

        return records.groupBy { it.bookingId }.map { (bId, bRecords) ->
            val bookingRecord = records.first { it.bookingId == bId }
            val bookingEntries = bRecords.map { r ->
                BookingEntry(
                    r.bookingEntryId,
                    r.bookingEntryDescription,
                    r.accountId,
                    r.amountInCents,
                )
            }
            Booking(
                realmId,
                bId,
                bookingRecord.bookingDescription,
                bookingRecord.datetime,
                bookingEntries,
            )
        }
    }

    fun insert(
        connection: Connection,
        addBooking: AddBooking,
    ) {
        check(addBooking.entries.sumOf { it.amountInCents } == 0L) { "Booking entries do not sum to zero" }

        val nextBookingId = realmRepository.getNextBookingId(connection, addBooking.realmId)

        connection.prepareStatement(
            """
            INSERT INTO booking (realm_id, id, description, datetime) VALUES (?,?,?,?)
        """.trimIndent()
        ).use { preparedStatement ->
            preparedStatement.setString(1, addBooking.realmId)
            preparedStatement.setLong(2, nextBookingId)
            preparedStatement.setString(3, addBooking.description)
            preparedStatement.setTimestamp(4, Timestamp.from(addBooking.datetime))
            preparedStatement.executeUpdate()
        }

        addBooking.entries.forEachIndexed { index, bookingEntry ->
            insertEntry(
                connection,
                addBooking.realmId,
                nextBookingId,
                index,
                bookingEntry.description,
                bookingEntry.accountId,
                bookingEntry.amountInCents
            )
        }
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
            preparedStatement.setString(4, description)
            preparedStatement.setString(5, accountId)
            preparedStatement.setLong(6, amountInCents)
            preparedStatement.executeUpdate()
        }
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
