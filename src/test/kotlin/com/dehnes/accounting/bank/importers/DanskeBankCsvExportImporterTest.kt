package com.dehnes.accounting.bank.importers

import com.dehnes.accounting.api.dtos.DuplicationHandlerType
import com.dehnes.accounting.database.*
import com.dehnes.accounting.database.Transactions.writeTx
import com.dehnes.accounting.datasourceSetup
import com.dehnes.accounting.objectMapper
import com.dehnes.accounting.services.BookingReadService
import com.dehnes.accounting.services.CategoryService
import com.dehnes.accounting.services.UserService
import mu.KotlinLogging
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.File
import java.sql.Connection
import java.time.LocalDate

@Disabled("Just for local testing")
class DanskeBankCsvExportImporterTest {

    @Test
    fun localTest() {
        val dataSource = datasourceSetup()
        val objectMapper = objectMapper()

        val repository = Repository(Changelog(objectMapper))
        val bankTransactionImportService = BankTransactionImportService(
            dataSource,
            repository,
            BookingReadService(repository, CategoryService(repository), dataSource, UserService(dataSource))
        )

        val danskeBank = BankDto(
            "8a8bd63b-bd2d-4ab3-8260-a757d21198a4",
            "DanskeBank",
            null,
            SupportedImporters.DanskeBankCsvExportImporter.name
        )

        val ledgerDto = LedgerDto(
            "892bec78-75ad-40ee-8fef-3d9bdf0e6b44",
            "Regningskonto",
            null,
            0
        )

        val bankAccountDto = BankAccountDto(
            "e0cdf548-be00-4cbb-b2a0-6c901c064029",
            "Felles",
            null,
            ledgerDto.id,
            danskeBank.id,
            "123",
            LocalDate.parse("2000-01-01"),
            null,
            4450701,
            0,
            0,
            0,
        )

        dataSource.writeTx { conn: Connection ->
            repository.addOrReplaceBank(conn, "me", danskeBank)
            repository.addOrReplaceLedger(conn, "me", ledgerDto)
            repository.getBankAccount(conn, bankAccountDto.id) ?: run {
                repository.addBankAccount(conn, "me", bankAccountDto)
            }
        }

        val doImport = bankTransactionImportService.doImport(
            "me",
            ledgerDto.id,
            bankAccountDto.id,
            File("/Users/sebas/Downloads/Regningskonto-81201351715-20230921.csv").inputStream(),
            "",
            DuplicationHandlerType.sameDateAmountAndDescription.duplicationHandler
        )

        KotlinLogging.logger { }.info { "Done $doImport" }

    }
}