package com.dehnes.accounting.bank

import com.dehnes.accounting.api.dtos.BookingRule
import com.dehnes.accounting.api.dtos.BookingRuleType
import com.dehnes.accounting.api.dtos.TransactionMatcherTarget
import com.dehnes.accounting.api.dtos.TransactionMatcherTargetType
import com.dehnes.accounting.database.BankTransaction
import com.dehnes.accounting.database.BookingAdd
import com.dehnes.accounting.database.BookingRecordAdd
import com.dehnes.accounting.database.Repository
import com.dehnes.accounting.database.Transactions.writeTx
import com.dehnes.accounting.services.BookingReadService
import javax.sql.DataSource

class TransactionMatchingService(
    private val repository: Repository,
    private val dataSource: DataSource,
    private val bookingReadService: BookingReadService,
) {


    fun executeMatch(userId: String, ledgerId: String, bankAccountId: String, transactionId: Long, matcherId: String) {
        val ledger = bookingReadService.listLedgers(userId, write = true).firstOrNull { it.id == ledgerId }
            ?: error("User $userId has not access to $ledgerId")

        dataSource.writeTx { conn ->
            val bankAccountDto =
                (repository.getAllBankAccountsForLedger(conn, ledger.id).firstOrNull { it.id == bankAccountId }
                    ?: error("No such bankId $bankAccountId"))

            val bankTransaction = repository.getBankTransaction(conn, bankAccountDto.id, transactionId)
            check(bankTransaction.matchedLedgerId == null) { "transaction $transactionId already matched" }

            val matcher = repository.getAllMatchers(conn)
                .filter { it.filters.all { it.type.fn(bankTransaction, it) } }
                .firstOrNull { it.id == matcherId } ?: error("No matcher with id $matcherId matches")

            val booking: BookingAdd = matcher.target.createBooking(ledgerId, bankTransaction)

            repository.addBooking(conn, userId, booking)
        }
    }
}

fun createRecord(
    bookingRule: BookingRule,
    remaining: Long,
): BookingRecordAdd {

    val amount: Long = when (bookingRule.type) {
        BookingRuleType.categoryBookingRemaining -> remaining
        BookingRuleType.categoryBookingFixedAmount -> bookingRule.amountInCents!!
    }

    return BookingRecordAdd(
        null,
        bookingRule.categoryId,
        amount,
        null,
        null,
    )
}

fun TransactionMatcherTarget.createBooking(
    ledgerId: String,
    bankTransaction: BankTransaction
): BookingAdd {
    return BookingAdd(
        ledgerId,
        null,
        bankTransaction.datetime,
        when (this.type) {
            TransactionMatcherTargetType.multipleCategoriesBooking -> {

                val wrapper = this.multipleCategoriesBooking!!

                var remainingDebit = bankTransaction.amount * -1
                var remainingCredit = bankTransaction.amount

                val debitRecords = wrapper.debitRules.map { bookingRule ->
                    createRecord(bookingRule, remainingDebit).apply {
                        remainingDebit -= this.amount
                    }
                }
                val creditRecords = wrapper.creditRules.map { bookingRule ->
                    createRecord(bookingRule, remainingCredit).apply {
                        remainingCredit -= this.amount
                    }
                }


                debitRecords + creditRecords.mapIndexed { index, bookingRecordAdd ->
                    if (index == 0) {
                        bookingRecordAdd.copy(
                            matchedBankAccountId = bankTransaction.bankAccountId,
                            matchedBankTransactionId = bankTransaction.id
                        )
                    } else {
                        bookingRecordAdd
                    }
                }
            }

            else -> TODO("")
        }
    )
}

