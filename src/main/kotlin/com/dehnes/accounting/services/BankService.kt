package com.dehnes.accounting.services

import com.dehnes.accounting.api.dtos.BankAccountTransactionView
import com.dehnes.accounting.api.dtos.BankAccountView
import com.dehnes.accounting.api.dtos.BankView
import com.dehnes.accounting.database.BankAccountDto
import com.dehnes.accounting.database.BankAccountTransactionsFilter
import com.dehnes.accounting.database.Repository
import com.dehnes.accounting.database.Transactions.readTx
import com.dehnes.accounting.database.Transactions.writeTx
import java.sql.Connection
import javax.sql.DataSource

class BankService(
    private val bookingReadService: BookingReadService,
    private val repository: Repository,
    private val dataSource: DataSource,
) {

    fun removeLastBankTransactions(
        userId: String,
        ledgerId: String,
        bankAccountId: String,
    ) {
        dataSource.writeTx { conn ->
            bookingReadService.getLedgerAuthorized(conn, userId, ledgerId, write = true)


            repository.removeLastBankTransaction(
                conn,
                userId,
                ledgerId,
                bankAccountId
            )
        }
    }
    fun getTransaction(
        userId: String,
        ledgerId: String,
        bankAccountId: String,
        transactionId: Long,
    ) = dataSource.readTx { conn ->
        val ledger = bookingReadService.getLedgerAuthorized(conn, userId, ledgerId, write = false)

        if (!repository.getAllBankAccountsForLedger(conn, ledger.id).any { it.id == bankAccountId }) {
            error("User $userId does not have access to this bankAccount")
        }

        val bankTransaction = repository.getBankTransaction(
            conn,
            bankAccountId,
            transactionId,
            ledgerId,
        )
        BankAccountTransactionView(
            bankTransaction.id,
            bankTransaction.description,
            bankTransaction.datetime,
            bankTransaction.amount,
            bankTransaction.balance,
            bankTransaction.matchedLedgerId != null,
        )
    }

    fun getTransactions(
        userId: String,
        ledgerId: String,
        bankAccountId: String,
        vararg filters: BankAccountTransactionsFilter
    ): List<BankAccountTransactionView> {

        return dataSource.readTx { conn ->
            val ledger = bookingReadService.getLedgerAuthorized(conn, userId, ledgerId, write = false)

            if (!repository.getAllBankAccountsForLedger(conn, ledger.id).any { it.id == bankAccountId }) {
                error("User $userId does not have access to this bankAccount")
            }

            repository.getBankTransactions(conn, bankAccountId, Int.MAX_VALUE, *filters).map {
                BankAccountTransactionView(
                    it.id,
                    it.description,
                    it.datetime,
                    it.amount,
                    it.balance,
                    it.matchedLedgerId != null
                )
            }
        }
    }

    fun getAllAccountsFor(userId: String, ledgerId: String) = dataSource.readTx {

        val allBanks = repository.getAllBanks(it)

        getAccountsWithSummary(it, userId, ledgerId).map { a ->
            BankAccountView(
                a.id,
                a.name,
                a.description,
                BankView.fromDto(allBanks.first { it.id == a.bankId }),
                a.closeDate != null,
                a.accountNumber,
                a.transactionsCounterUnbooked,
                a.currentBalance
            )
        }
    }

    fun getAccountsWithSummary(conn: Connection, userId: String, ledgerId: String): List<BankAccountDto> {
        val ledger = bookingReadService.getLedgerAuthorized(conn, userId, ledgerId, write = false)

        return repository.getAllBankAccountsForLedger(conn, ledger.id)
    }

}
