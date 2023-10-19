package com.dehnes.accounting.services

import com.dehnes.accounting.database.*
import com.dehnes.accounting.datasourceSetup
import com.dehnes.accounting.dbFile
import com.dehnes.accounting.objectMapper
import com.dehnes.accounting.utils.DateTimeUtils.zoneId
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.*

@Disabled
class OverviewRapportServiceTest {

    @Test
    fun test() {
        val dataSource = datasourceSetup(dbFile())
        val accountsRepository = AccountsRepository(dataSource, Changelog())
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

    @Test
    fun test2() {
        val objectMapper = objectMapper()

        val m = UnbookedBankTransactionMatcher(
            UUID.randomUUID().toString(),
            "realmId",
            "name",
            AndFilters(listOf(
                ContainsFilter("contains")
            )) ,
            TransferAction,
            "",
            "",
            Instant.now()
        )

        val json = objectMapper.writeValueAsString(m)
        println()
    }
}