package com.dehnes.accounting.database

import com.dehnes.accounting.api.ReadService
import com.dehnes.accounting.configuration
import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant

class Changelog(
    private val objectMapper: ObjectMapper,
    private val requireReadService: Boolean = true
) {

    private var readService: ReadService? = null

    private fun readService(): ReadService? {
        if (readService == null) {
            readService = configuration.getBeanNull()
        }
        check(!requireReadService || readService != null)
        return readService;
    }

    fun add(connection: Connection, userId: String, type: ChangeLogEventType, value: Any) {
        connection.prepareStatement(
            "INSERT INTO changelog (change_type, change_value, created, created_by_user_id) VALUES (?,?,?,?)"
        )
            .use { preparedStatement ->
                preparedStatement.setString(1, type.name)
                preparedStatement.setString(2, objectMapper.writeValueAsString(value))
                preparedStatement.setTimestamp(3, Timestamp.from(Instant.now()))
                preparedStatement.setString(4, userId)

                check(preparedStatement.executeUpdate() == 1) { "Could not insert changelog" }
            }

        readService()?.onChangelogEvent(type)
    }

}

enum class ChangeLogEventType {
    bankAdded,
    bankUpdated,
    bankRemoved,

    userAdded,
    userUpdated,

    legderAdded,
    legderUpdated,
    legderRemoved,

    bankAccountAdded,
    bankAccountUpdated,
    bankAccountRemoved,

    userLedgerAccessChanged,

    categoryAdded,
    categoryUpdated,
    categoryRemoved,

    matcherAdded,
    matcherUpdated,
    matcherRemoved,

    bankTransactionRemoveLast,
    bankTransactionAdded,

    bookingAdded,
    bookingRemoved,

    userStateUpdated,

}

