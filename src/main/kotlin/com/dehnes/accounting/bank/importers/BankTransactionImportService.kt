package com.dehnes.accounting.bank.importers

import com.dehnes.accounting.database.*
import com.dehnes.accounting.services.AccessRequest
import com.dehnes.accounting.services.AuthorizationService
import java.io.InputStream
import java.time.temporal.ChronoUnit

class BankTransactionImportService(
    private val authorizationService: AuthorizationService,
    private val bankAccountRepository: BankAccountRepository,
    private val unbookedTransactionRepository: UnbookedTransactionRepository,
    private val bankRepository: BankRepository,
    private val changelog: Changelog,
    private val bookingRepository: BookingRepository
) {
    fun doImport(
        userId: String,
        realmId: String,
        accountId: String,
        data: InputStream,
        filename: String,
    ) = changelog.writeTx { conn ->


        val bankAccount =
            bankAccountRepository.getAllBankAccounts(conn, realmId).firstOrNull { it.accountId == accountId }
                ?: error("No such bank account $accountId")

        val bankDto = bankRepository.getAll(conn).firstOrNull { it.id == bankAccount.bankId }
            ?: error("No such bank ${bankAccount.bankId}")

        val importer = bankDto.transactionImportFunction?.let {
            SupportedImporters.valueOf(it)
        }?.klazz ?: error("No importer configured for bank ${bankDto.id}")

        val importInstance = importer.java.constructors.first().newInstance() as Importer

        var imported = 0L
        var skipped = 0L

        importInstance.import(
            data,
            filename
        ) { record ->

            authorizationService.assertAuthorization(
                conn,
                userId,
                realmId,
                AccessRequest.write,
                record.datetime
            )

            // check duplicates
            val hasDuplicate = bookingRepository.getBookingsForRange(
                realmId,
                DateRangeFilter(
                    record.datetime.minus(1, ChronoUnit.DAYS),
                    record.datetime.plus(1, ChronoUnit.DAYS),
                )
            ).any {
                val thisAccountEntries = it.entries.filter { it.accountId == accountId }
                thisAccountEntries.sumOf { it.amountInCents } == record.amountInCents
            }

            if (hasDuplicate) {
                skipped++
            } else {
                unbookedTransactionRepository.insert(
                    conn,
                    UnbookedTransaction(
                        accountId,
                        realmId,
                        0,
                        record.description,
                        record.datetime,
                        record.amountInCents,
                        record.otherAccountNumber
                    )
                )
                imported++
            }

        }

        ImportResult(
            imported,
            skipped,
        )
    }
}


data class ImportResult(
    val imported: Long,
    val skipped: Long,
)


