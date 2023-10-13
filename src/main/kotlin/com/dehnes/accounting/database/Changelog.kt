package com.dehnes.accounting.database

import com.dehnes.accounting.api.ChangeEvent
import com.dehnes.accounting.api.ChangeLogEventTypeV2
import com.dehnes.accounting.api.ReadService
import com.dehnes.accounting.configuration
import java.sql.Connection

class Changelog(
    private val requireReadService: Boolean = true
) {

    private var readService: ReadService? = null

    private fun readService(): ReadService? {
        if (readService == null) {
            readService = configuration.getBeanNull()
        }
        check(!requireReadService || readService != null)
        return readService
    }

    fun add(connection: Connection, userId: String, type: ChangeLogEventType, value: Any) {
        readService()?.onChangelogEvent(type)
    }
    fun addV2(type: ChangeLogEventTypeV2) {
        readService()?.onChangelogEvent(ChangeEvent(null, type, null))
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
    bookingChanged,

    userStateUpdated
}

