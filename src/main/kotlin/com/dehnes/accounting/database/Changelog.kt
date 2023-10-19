package com.dehnes.accounting.database

import com.dehnes.accounting.api.ChangeEvent
import com.dehnes.accounting.api.ChangeLogEventTypeV2
import com.dehnes.accounting.api.ReadService
import com.dehnes.accounting.configuration

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

    fun add(type: ChangeLogEventTypeV2) {
        readService()?.onChangelogEvent(ChangeEvent(type, null))
    }
}
