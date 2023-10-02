package com.dehnes.accounting.bank

import com.dehnes.accounting.api.dtos.*
import com.dehnes.accounting.database.*
import com.dehnes.accounting.database.Transactions.readTx
import com.dehnes.accounting.database.Transactions.writeTx
import com.dehnes.accounting.services.BookingReadService
import com.dehnes.accounting.services.Categories
import com.dehnes.accounting.services.CategoryService
import com.dehnes.smarthome.utils.DateTimeUtils.plusDays
import java.sql.Connection
import javax.sql.DataSource
import kotlin.math.absoluteValue

class TransactionMatchingService(
    private val repository: Repository,
    private val dataSource: DataSource,
    private val bookingReadService: BookingReadService,
    private val categoryService: CategoryService,
) {

    fun addOrReplaceMatcher(
        userId: String,
        matcher: TransactionMatcher
    ) {

        dataSource.writeTx { conn ->
            bookingReadService.listLedgers(conn, userId, write = true).firstOrNull { it.id == matcher.ledgerId }
                ?: error("User $userId has not access to ${matcher.ledgerId}")

            repository.addOrReplaceMatcher(
                conn,
                userId,
                matcher
            )
        }
    }

    fun getMatchers(
        userId: String,
        ledgerId: String,
        testMatchFor: TestMatchFor?,
    ): GetMatchersResponse {

        return dataSource.readTx { conn ->
            val ledger = bookingReadService.listLedgers(conn, userId, write = false).firstOrNull { it.id == ledgerId }
                ?: error("User $userId has not access to $ledgerId")

            val allMatchers = repository.getAllMatchers(conn, ledgerId)

            val macherIdsWhichMatched = testMatchFor?.let { t ->
                val bankAccountDto =
                    (repository.getAllBankAccountsForLedger(conn, ledger.id).firstOrNull { it.id == t.bankAccountId }
                        ?: error("No such bankId ${t.bankAccountId}"))

                val bankTransaction = repository.getBankTransaction(conn, bankAccountDto.id, t.transactionId)

                if (bankTransaction.matchedLedgerId == null) {
                    allMatchers
                        .filter { it.filters.all { it.type.fn(bankAccountDto, bankTransaction, it) } }
                        .map { it.id }
                } else emptyList()

            } ?: emptyList()

            GetMatchersResponse(
                allMatchers,
                macherIdsWhichMatched
            )
        }
    }

    fun deleteMatcher(
        userId: String,
        ledgerId: String,
        matcherId: String,
    ) {
        dataSource.writeTx { conn ->
            bookingReadService.listLedgers(conn, userId, write = true).firstOrNull { it.id == ledgerId }
                ?: error("User $userId has not access to $ledgerId")

            repository.removeMatcher(conn, userId, matcherId)
        }
    }

    fun executeMatch(
        userId: String,
        ledgerId: String,
        bankAccountId: String,
        transactionId: Long,
        matcherId: String,
        memoText: String?,
    ) {
        dataSource.writeTx { conn ->
            val ledger = bookingReadService.listLedgers(conn, userId, write = true).firstOrNull { it.id == ledgerId }
                ?: error("User $userId has not access to $ledgerId")
            val bankAccountDto =
                (repository.getAllBankAccountsForLedger(conn, ledger.id).firstOrNull { it.id == bankAccountId }
                    ?: error("No such bankId $bankAccountId"))

            val bankTransaction = repository.getBankTransaction(conn, bankAccountDto.id, transactionId)
            check(bankTransaction.matchedLedgerId == null) { "transaction $transactionId already matched" }

            val matcher = repository.getAllMatchers(conn, ledgerId)
                .filter { it.filters.all { it.type.fn(bankAccountDto, bankTransaction, it) } }
                .firstOrNull { it.id == matcherId } ?: error("No matcher with id $matcherId matches")

            check(matcher.ledgerId == ledger.id)

            val result = matcher.target.createBooking(
                ledgerId,
                bankTransaction,
                bankAccountDto,
                conn,
                categoryService.get(conn),
                memoText,
            )

            if (result.booking != null) {
                repository.addBooking(conn, userId, result.booking)
            }

            if (result.matchBookingId != null && result.matchBookingRecordId != null) {
                repository.matchBankTransaction(
                    conn,
                    userId,
                    bankAccountId,
                    bankTransaction.id,
                    ledgerId,
                    result.matchBookingId,
                    result.matchBookingRecordId,
                )
            }

            repository.matchUsed(conn, matcherId)
        }
    }

    private fun TransactionMatcherTarget.createBooking(
        ledgerId: String,
        bankTransaction: BankTransaction,
        bankAccountDto: BankAccountDto,
        connection: Connection,
        categories: Categories,
        memoText: String?,
    ): TargetResult {
        val records = when (this.type) {
            TransactionMatcherTargetType.multipleCategoriesBooking -> {

                val wrapper = this.multipleCategoriesBooking!!

                var remainingDebit = bankTransaction.amount * -1
                var remainingCredit = bankTransaction.amount

                val debitRecords = wrapper.debitRules.map { bookingRule ->
                    createRecord(bookingRule, remainingDebit, memoText).apply {
                        remainingDebit -= this.amount
                    }
                }
                val creditRecords = wrapper.creditRules.map { bookingRule ->
                    createRecord(bookingRule, remainingCredit, memoText).apply {
                        remainingCredit -= this.amount
                    }
                }


                creditRecords + debitRecords
            }

            TransactionMatcherTargetType.bankTransfer -> {
                val otherCategoryId = transferCategoryId!!
                val thisCategoryId = bankAccountDto.categoryId

                val otherBankAccount = repository
                    .getAllBankAccountsForLedger(connection, ledgerId)
                    .first { it.categoryId == otherCategoryId }

                val candidateTransactions = repository.getBankTransactions(
                    connection,
                    otherBankAccount.id,
                    Int.MAX_VALUE,
                    BankTxDateRangeFilter(
                        bankTransaction.datetime,
                        bankTransaction.datetime.plusDays(1),
                    )
                )

                val candidates = repository.getBookings(
                    connection,
                    categories,
                    ledgerId,
                    Int.MAX_VALUE,
                    DateRangeFilter(
                        bankTransaction.datetime,
                        bankTransaction.datetime.plusDays(1),
                    )
                )
                    .filter { b ->
                        b.records.size == 2 &&
                                b.records.map { it.category.id }.toSet() == setOf(otherCategoryId, thisCategoryId)
                    }
                    .filter { it.records.all { it.amount.absoluteValue == bankTransaction.amount.absoluteValue } }
                    .filter { b ->

                        repository.findMatchedTransactions(
                            connection,
                            ledgerId,
                            b.id,
                        )

                        val recordId = b.records.single { it.category.id == otherCategoryId }

                        candidateTransactions.any { tx ->
                            tx.matchedLedgerId == ledgerId && tx.matchedBookingId == b.id
                                    && tx.matchedBookingRecordId == recordId.id
                        }
                    }


                // if there is an existing booking which matches, and
                // there the "this" record is not matched -> create match without Booking
                if (candidates.isNotEmpty()) {
                    return TargetResult(
                        null,
                        candidates.first().id,
                        candidates.first().records.first { it.category.id == thisCategoryId }.id
                    )
                }

                listOf(
                    BookingRecordAdd(
                        description = memoText,
                        categoryId = thisCategoryId,
                        amount = bankTransaction.amount,
                        matchedBankAccountId = null,
                        matchedBankTransactionId = null
                    ),
                    BookingRecordAdd(
                        description = memoText,
                        categoryId = otherCategoryId,
                        amount = bankTransaction.amount * -1,
                        matchedBankAccountId = null,
                        matchedBankTransactionId = null
                    )
                )
            }
        }

        return TargetResult(
            BookingAdd(
                ledgerId,
                null,
                bankTransaction.datetime,
                records.mapIndexed { index, bookingRecordAdd ->
                    if (index == 0) {
                        bookingRecordAdd.copy(
                            matchedBankAccountId = bankTransaction.bankAccountId,
                            matchedBankTransactionId = bankTransaction.id
                        )
                    } else {
                        bookingRecordAdd
                    }
                }
            ),
            null,
            null,
        )
    }
}

fun createRecord(
    bookingRule: BookingRule,
    remaining: Long,
    memoText: String?,
): BookingRecordAdd {

    val amount: Long = when (bookingRule.type) {
        BookingRuleType.categoryBookingRemaining -> remaining
        BookingRuleType.categoryBookingFixedAmount -> bookingRule.amountInCents!!
    }

    return BookingRecordAdd(
        memoText,
        bookingRule.categoryId,
        amount,
        null,
        null,
    )
}

data class TargetResult(
    val booking: BookingAdd?,
    val matchBookingId: Long?,
    val matchBookingRecordId: Long?,
)



