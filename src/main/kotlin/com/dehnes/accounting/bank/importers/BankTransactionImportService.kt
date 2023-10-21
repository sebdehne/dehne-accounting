package com.dehnes.accounting.bank.importers

import com.dehnes.accounting.database.*
import com.dehnes.accounting.database.Transactions.writeTx
import com.dehnes.accounting.services.AccessRequest
import com.dehnes.accounting.services.AuthorizationService
import com.dehnes.accounting.services.BankAccountService
import com.dehnes.accounting.services.BankAccountTransaction
import com.dehnes.accounting.utils.DateTimeUtils.plusDays
import java.io.InputStream
import javax.sql.DataSource

class BankTransactionImportService(
    private val dataSource: DataSource,
    private val authorizationService: AuthorizationService,
    private val bankAccountRepository: BankAccountRepository,
    private val bankAccountService: BankAccountService,
    private val unbookedTransactionRepository: UnbookedTransactionRepository,
    private val bankRepository: BankRepository,
) {
    fun doImport(
        userId: String,
        realmId: String,
        accountId: String,
        data: InputStream,
        filename: String,
        duplicationHandler: DuplicationHandler,
    ) = dataSource.writeTx { conn ->

        authorizationService.assertAuthorization(conn, userId, realmId, AccessRequest.write)

        val bankAccount =
            bankAccountRepository.getAllBankAccounts(conn, realmId).firstOrNull { it.accountId == accountId }
                ?: error("No such bank account $accountId")

        val bankDto = bankRepository.getAll(conn).firstOrNull { it.id == bankAccount.bankId }
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
                val existingRecords = bankAccountService.getBankAccountTransactions(
                    conn,
                    realmId,
                    accountId,
                    DateRangeFilter(
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

typealias DuplicationHandler = (existing: BankAccountTransaction, record: BankTransactionImportRecord) -> Boolean

