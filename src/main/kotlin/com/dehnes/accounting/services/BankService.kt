package com.dehnes.accounting.services

import com.dehnes.accounting.api.dtos.BankAccountTransactionView
import com.dehnes.accounting.api.dtos.BankAccountView
import com.dehnes.accounting.api.dtos.BankView
import com.dehnes.accounting.database.AccessRequest
import com.dehnes.accounting.database.BankAccountDto
import com.dehnes.accounting.database.BankAccountTransactionsFilter
import com.dehnes.accounting.database.Repository
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
            bookingReadService.getLedgerAuthorized(conn, userId, ledgerId, AccessRequest.write)


            repository.removeLastBankTransaction(
                conn,
                userId,
                ledgerId,
                bankAccountId
            )
        }
    }

    fun getTransaction(
        conn: Connection,
        userId: String,
        ledgerId: String,
        bankAccountId: String,
        transactionId: Long,
    ): BankAccountTransactionView {
        val ledger = bookingReadService.getLedgerAuthorized(conn, userId, ledgerId, AccessRequest.read)

        if (!repository.getAllBankAccountsForLedger(conn, ledger.id).any { it.id == bankAccountId }) {
            error("User $userId does not have access to this bankAccount")
        }

        val bankTransaction = repository.getBankTransaction(
            conn,
            bankAccountId,
            transactionId,
            ledgerId,
        )
        return BankAccountTransactionView(
            bankTransaction.id,
            bankTransaction.description,
            bankTransaction.datetime,
            bankTransaction.amount,
            bankTransaction.balance,
            bankTransaction.matchedLedgerId != null,
        )
    }
    fun getTransactions(
        conn: Connection,
        userId: String,
        ledgerId: String,
        bankAccountId: String,
        vararg filters: BankAccountTransactionsFilter
    ): List<BankAccountTransactionView> {

        val ledger = bookingReadService.getLedgerAuthorized(conn, userId, ledgerId, AccessRequest.read)

        if (!repository.getAllBankAccountsForLedger(conn, ledger.id).any { it.id == bankAccountId }) {
            error("User $userId does not have access to this bankAccount")
        }

        return repository.getBankTransactions(conn, bankAccountId, Int.MAX_VALUE, *filters).map {
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

    fun getAllAccountsFor(conn: Connection, userId: String, ledgerId: String): List<BankAccountView> {
        val allBanks = repository.getAllBanks(conn)

        return getAccountsWithSummary(conn, userId, ledgerId).map { a ->
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

    private fun getAccountsWithSummary(conn: Connection, userId: String, ledgerId: String): List<BankAccountDto> {
        val ledger = bookingReadService.getLedgerAuthorized(conn, userId, ledgerId, AccessRequest.read)

        return repository.getAllBankAccountsForLedger(conn, ledger.id)
    }

}
