package com.dehnes.accounting.services

import com.dehnes.accounting.api.dtos.BankAccountTransactionView
import com.dehnes.accounting.api.dtos.BankAccountView
import com.dehnes.accounting.api.dtos.BankView
import com.dehnes.accounting.database.BankAccountDto
import com.dehnes.accounting.database.BankAccountTransactionsFilter
import com.dehnes.accounting.database.Repository
import com.dehnes.accounting.database.Transactions.readTx
import java.sql.Connection
import javax.sql.DataSource

class BankService(
    private val bookingReadService: BookingReadService,
    private val repository: Repository,
    private val dataSource: DataSource,
) {

    fun getTransactions(
        userId: String,
        ledgerId: String,
        bankAccountId: String,
        vararg filters: BankAccountTransactionsFilter
    ): List<BankAccountTransactionView> {

        return dataSource.readTx { conn ->
            val ledgerDto = bookingReadService.listLedgers(conn, userId).firstOrNull { it.id == ledgerId }
                ?: error("Could not access ledgerId=$ledgerId for userId=$userId")

            if (!repository.getAllBankAccountsForLedger(conn, ledgerDto.id).any { it.id == bankAccountId }) {
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

    fun getAccountsWithSummary(connection: Connection, userId: String, ledgerId: String): List<BankAccountDto> {
        val ledgerDto = bookingReadService.listLedgers(connection, userId).firstOrNull { it.id == ledgerId }
            ?: error("Could not access ledgerId=$ledgerId for userId=$userId")

        return repository.getAllBankAccountsForLedger(connection, ledgerDto.id)
    }

}
