package no.dehnes.accounting

import com.dehnes.accounting.bank.TransactionMatchingService
import com.dehnes.accounting.database.Changelog
import com.dehnes.accounting.database.Repository
import com.dehnes.accounting.datasourceSetup
import com.dehnes.accounting.objectMapper
import com.dehnes.accounting.services.BookingReadService
import com.dehnes.accounting.services.UserService
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

@Disabled("Local testing")
class MatcherTest {

    @Test
    fun test() {
        val dataSource = datasourceSetup()
        val objectMapper = objectMapper()
        val changelog = Changelog(objectMapper, false)

        val repository = Repository(changelog, objectMapper)

        val transactionMatchingService = TransactionMatchingService(
            repository,
            dataSource,
            BookingReadService(repository, dataSource, UserService(dataSource)),
        )

        transactionMatchingService.executeMatch(
            "dbf21a6c-16e2-4689-be24-1766777a70da",
            "a7fe6304-6d23-34ab-96ce-13e59c6160a2",
            "fc466a0c-633c-381d-bee4-6b67e8d41ab1",
            9485,
            "4e7a0323-e43d-3b20-b967-1d54d2edfb95"
        )
    }
}