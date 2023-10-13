package com.dehnes.accounting.services

import com.dehnes.accounting.database.AccountsRepository
import com.dehnes.accounting.database.BookingRepository
import com.dehnes.accounting.database.DateRangeFilter
import com.dehnes.accounting.database.RealmRepository
import com.dehnes.accounting.datasourceSetup
import com.dehnes.accounting.dbFile
import com.dehnes.accounting.utils.DateTimeUtils.zoneId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*

@Disabled
class OverviewRapportServiceTest {

    @Test
    fun test() {
        val dataSource = datasourceSetup(dbFile())
        val accountsRepository = AccountsRepository(dataSource)
        val overviewRapportService = OverviewRapportService(
            dataSource,
            BookingRepository(RealmRepository(dataSource, accountsRepository)),
            accountsRepository
        )

        val rapport = overviewRapportService.createRapport(
            UUID.nameUUIDFromBytes("felles".toByteArray()).toString(),
            DateRangeFilter(
                LocalDate.parse("2023-08-01").atStartOfDay().atZone(zoneId).toInstant(),
                LocalDate.parse("2023-09-01").atStartOfDay().atZone(zoneId).toInstant(),
            )
        )

        println()
    }
}