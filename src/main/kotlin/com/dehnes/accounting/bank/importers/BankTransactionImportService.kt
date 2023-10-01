package com.dehnes.accounting.bank.importers

import com.dehnes.accounting.database.BankTransaction
import com.dehnes.accounting.database.BankTransactionAdd
import com.dehnes.accounting.database.BankTxDateRangeFilter
import com.dehnes.accounting.database.Repository
import com.dehnes.accounting.database.Transactions.writeTx
import com.dehnes.accounting.services.BookingReadService
import com.dehnes.smarthome.utils.DateTimeUtils.plusDays
import java.io.InputStream
import javax.sql.DataSource

class BankTransactionImportService(
    private val dataSource: DataSource,
    private val repository: Repository,
    private val bookingReadService: BookingReadService,
) {
    fun doImport(
        userId: String,
        ledgerId: String,
        bankAccountId: String,
        data: InputStream,
        filename: String,
        duplicationHandler: DuplicationHandler,
    ): ImportResult = try {
        dataSource.writeTx { conn ->

            val ledgerDto = bookingReadService.listLedgers(conn, userId, write = true).firstOrNull { it.id == ledgerId }
                ?: error("Could not access ledgerId=$ledgerId for userId=$userId")

            val bankAccount =
                repository.getAllBankAccountsForLedger(conn, ledgerDto.id).firstOrNull { it.id == bankAccountId }
                    ?: error("No such bank account $bankAccountId")

            val bankDto = repository.getAllBanks(conn).firstOrNull { it.id == bankAccount.bankId }
                ?: error("No such bank ${bankAccount.bankId}")

            val importer = bankDto.transactionImportFunction?.let {
                SupportedImporters.valueOf(it)
            }?.klazz ?: error("No importer configured for bank ${bankDto.id}")

            val importInstance = importer.java.constructors.first().newInstance() as Importer

            var overlapping = true
            var skipped = 0L
            var imported = 0L

            importInstance.import(
                data,
                filename
            ) { record ->

                if (overlapping) {
                    val existingRecords = repository.getBankTransactions(
                        connection = conn,
                        bankAccountId = bankAccountId,
                        limit = Int.MAX_VALUE,
                        BankTxDateRangeFilter(
                            record.datetime,
                            record.datetime.plusDays(1)
                        )
                    ).filter { duplicationHandler(it, record) }

                    if (existingRecords.isNotEmpty()) {
                        skipped++
                        return@import
                    } else {
                        overlapping = false
                    }
                }

                repository.addBankTransaction(
                    conn,
                    userId,
                    BankTransactionAdd(
                        record.description,
                        bankAccount.ledgerId,
                        bankAccount.bankId,
                        bankAccount.id,
                        record.datetime,
                        record.amountInCents,
                    )
                )
                imported++
            }

            ImportResult(
                imported,
                skipped,
                null,
            )
        }
    } catch (e: Throwable) {
        ImportResult(0, 0, e.localizedMessage)
    }

}


data class ImportResult(
    val imported: Long,
    val skipped: Long,
    val error: String?,
)

typealias DuplicationHandler = (existing: BankTransaction, record: BankTransactionImportRecord) -> Boolean

