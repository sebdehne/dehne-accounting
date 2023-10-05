package com.dehnes.accounting.bank

import com.dehnes.accounting.api.dtos.*
import com.dehnes.accounting.database.*
import com.dehnes.accounting.database.Transactions.readTx
import com.dehnes.accounting.database.Transactions.writeTx
import com.dehnes.accounting.services.BookingReadService
import com.dehnes.smarthome.utils.DateTimeUtils.plusDays
import java.sql.Connection
import javax.sql.DataSource
import kotlin.math.absoluteValue

class TransactionMatchingService(
    private val repository: Repository,
    private val dataSource: DataSource,
    private val bookingReadService: BookingReadService,
) {

    fun addOrReplaceMatcher(
        userId: String,
        matcher: TransactionMatcher
    ) {

        dataSource.writeTx { conn ->
            bookingReadService.getLedgerAuthorized(conn, userId, matcher.ledgerId, AccessRequest.write)

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
            val ledger = bookingReadService.getLedgerAuthorized(conn, userId, ledgerId, AccessRequest.read)

            val allMatchers = repository.getAllMatchers(conn, ledgerId)

            val macherIdsWhichMatched = testMatchFor?.let { t ->
                val bankAccountDto =
                    (repository.getAllBankAccountsForLedger(conn, ledger.id).firstOrNull { it.id == t.bankAccountId }
                        ?: error("No such bankId ${t.bankAccountId}"))

                val bankTransaction = repository.getBankTransaction(
                    conn,
                    bankAccountDto.id,
                    t.transactionId,
                    ledgerId,
                )

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
            bookingReadService.getLedgerAuthorized(conn, userId, ledgerId, AccessRequest.write)

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
            val ledger = bookingReadService.getLedgerAuthorized(conn, userId, ledgerId, AccessRequest.write)

            val bankAccountDto =
                (repository.getAllBankAccountsForLedger(conn, ledger.id).firstOrNull { it.id == bankAccountId }
                    ?: error("No such bankId $bankAccountId"))

            val bankTransaction = repository.getBankTransaction(
                conn,
                bankAccountDto.id,
                transactionId,
                ledgerId,
            )
            check(bankTransaction.matchedLedgerId == null) { "transaction $transactionId already matched" }

            val matcher = repository.getAllMatchers(conn, ledgerId)
                .filter { it.filters.all { it.type.fn(bankAccountDto, bankTransaction, it) } }
                .firstOrNull { it.id == matcherId } ?: error("No matcher with id $matcherId matches")

            check(matcher.ledgerId == ledger.id)

            val result = matcher.action.createBooking(
                ledgerId,
                bankTransaction,
                bankAccountDto,
                conn,
                memoText,
            )

            val bookingIdToMatch = if (result.booking != null) {
                repository.addBooking(conn, userId, result.booking)
            } else {
                result.matchBookingId!!
            }

            repository.matchBankTransaction(
                conn,
                userId,
                bankAccountId,
                bankTransaction.id,
                ledgerId,
                bookingIdToMatch,
            )

            repository.matchUsed(conn, matcherId)
        }
    }

    private fun TransactionMatcherAction.createBooking(
        ledgerId: String,
        bankTransaction: BankTransaction,
        bankAccountDto: BankAccountDto,
        connection: Connection,
        memoText: String?,
    ): ActionResult {
        val records = when (this.type) {
            TransactionMatcherActionType.paymentOrIncome -> {

                val wrapper = this.paymentOrIncomeConfig!!

                val createRules = { startingAmount: Long, c: BookingConfigurationForOneSide ->
                    val rules = mutableListOf<BookingRecordAdd>()
                    var remaining = startingAmount
                    c.categoryToFixedAmountMapping.forEach { (categoryId, amountInCents) ->
                        rules.add(
                            BookingRecordAdd(
                                memoText,
                                categoryId,
                                amountInCents,
                            )
                        )
                        remaining -= amountInCents
                    }
                    rules.add(
                        BookingRecordAdd(
                            memoText,
                            c.categoryIdRemaining,
                            remaining,
                        )
                    )
                    rules
                }

                val mainRules = createRules(
                    bankTransaction.amount,
                    wrapper.mainSide
                )
                val negatedRules = createRules(
                    bankTransaction.amount * -1,
                    wrapper.negatedSide
                )

                mainRules + negatedRules
            }

            TransactionMatcherActionType.bankTransfer -> {
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
                    ledgerId,
                    Int.MAX_VALUE,
                    DateRangeFilter(
                        bankTransaction.datetime,
                        bankTransaction.datetime.plusDays(1),
                    )
                )
                    .filter { b ->
                        b.records.size == 2 &&
                                b.records.map { it.categoryId }.toSet() == setOf(otherCategoryId, thisCategoryId)
                    }
                    .filter { it.records.all { it.amount.absoluteValue == bankTransaction.amount.absoluteValue } }
                    .filter { b ->
                        candidateTransactions.any { tx ->
                            tx.matchedLedgerId == ledgerId && tx.matchedBookingId == b.id
                        }
                    }


                // if there is an existing booking which matches, and
                // there the "this" record is not matched -> create match without Booking
                if (candidates.isNotEmpty()) {
                    return ActionResult(
                        null,
                        candidates.first().id,
                    )
                }

                listOf(
                    BookingRecordAdd(
                        description = memoText,
                        categoryId = thisCategoryId,
                        amount = bankTransaction.amount,
                    ),
                    BookingRecordAdd(
                        description = memoText,
                        categoryId = otherCategoryId,
                        amount = bankTransaction.amount * -1,
                    )
                )
            }
        }

        return ActionResult(
            BookingAdd(
                ledgerId,
                null,
                bankTransaction.datetime,
                records,
            ),
            null,
        )
    }
}

data class ActionResult(
    val booking: BookingAdd?,
    val matchBookingId: Long?,
)

