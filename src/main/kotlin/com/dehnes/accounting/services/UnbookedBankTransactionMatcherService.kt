package com.dehnes.accounting.services

import com.dehnes.accounting.api.BookingsChanged
import com.dehnes.accounting.api.UnbookedTransactionMatchersChanged
import com.dehnes.accounting.api.UnbookedTransactionsChanged
import com.dehnes.accounting.api.dtos.ExecuteMatcherRequest
import com.dehnes.accounting.database.*
import com.dehnes.accounting.database.Transactions.readTx
import java.time.Instant
import javax.sql.DataSource

class UnbookedBankTransactionMatcherService(
    private val unbookedTransactionRepository: UnbookedTransactionRepository,
    private val authorizationService: AuthorizationService,
    private val unbookedBankTransactionMatcherRepository: UnbookedBankTransactionMatcherRepository,
    private val dataSource: DataSource,
    private val changelog: Changelog,
    private val bookingRepository: BookingRepository,
) {

    fun getTotalUnbookedTransactions(
        userId: String,
        realmId: String,
    ) = dataSource.readTx { conn ->
        authorizationService.assertAuthorization(
            conn,
            userId,
            realmId,
            AccessRequest.read,
        )

        unbookedTransactionRepository.getCount(conn, realmId, null)
    }

    fun executeMatcher(
        userId: String,
        realmId: String,
        executeMatcherRequest: ExecuteMatcherRequest,
    ) {
        changelog.writeTx { conn ->

            val unbookedTransaction = unbookedTransactionRepository.getUnbookedTransaction(
                conn,
                realmId,
                executeMatcherRequest.accountId,
                executeMatcherRequest.transactionId
            ) ?: return@writeTx

            authorizationService.assertAuthorization(
                conn,
                userId,
                realmId,
                AccessRequest.write,
                unbookedTransaction.datetime
            )

            val transactionMatcher =
                unbookedBankTransactionMatcherRepository.getAll(conn, realmId)
                    .single { m -> m.id == executeMatcherRequest.matcherId }

            val memo = executeMatcherRequest.overrideMemo?.ifBlank { null }
                ?: transactionMatcher.actionMemo?.ifBlank { null }

            val bookings: List<AddBooking> = when (transactionMatcher.action) {
                is TransferAction -> {
                    listOf(
                        AddBooking(
                            realmId = realmId,
                            description = memo,
                            datetime = unbookedTransaction.datetime,
                            entries = listOf(
                                AddBookingEntry(
                                    description = null,
                                    accountId = unbookedTransaction.accountId,
                                    amountInCents = unbookedTransaction.amountInCents,
                                    checked = false,
                                ),
                                AddBookingEntry(
                                    description = null,
                                    accountId = transactionMatcher.actionAccountId,
                                    amountInCents = unbookedTransaction.amountInCents * -1,
                                    checked = false,
                                )
                            ),
                            originalUnbookedTransaction = unbookedTransaction
                        )
                    )
                }

                is AccountAction -> {

                    val splits = mutableListOf<AddBookingEntry>()
                    val remaining =
                        transactionMatcher.action.additionalSplits.entries
                            .fold(unbookedTransaction.amountInCents * -1) { acc, entry ->
                                splits.add(
                                    AddBookingEntry(
                                        description = null,
                                        accountId = entry.key,
                                        amountInCents = entry.value,
                                        checked = false,
                                    )
                                )
                                acc - entry.value
                            }
                    splits.add(
                        AddBookingEntry(
                            description = null,
                            accountId = transactionMatcher.action.mainAccountId,
                            amountInCents = remaining,
                            checked = false,
                        )
                    )

                    listOf(
                        AddBooking(
                            realmId,
                            memo,
                            unbookedTransaction.datetime,
                            listOf(

                                // payable/receivable
                                AddBookingEntry(
                                    description = null,
                                    accountId = unbookedTransaction.accountId,
                                    amountInCents = unbookedTransaction.amountInCents,
                                    checked = false,
                                ),
                                AddBookingEntry(
                                    description = null,
                                    accountId = transactionMatcher.actionAccountId,
                                    amountInCents = unbookedTransaction.amountInCents * -1,
                                    checked = false,
                                ),

                                // income/expense
                                AddBookingEntry(
                                    description = null,
                                    accountId = transactionMatcher.actionAccountId,
                                    amountInCents = unbookedTransaction.amountInCents,
                                    checked = false,
                                ),
                            ) + splits,
                            unbookedTransaction
                        ),
                    )
                }
            }

            bookings.forEach { b ->
                bookingRepository.insert(conn, b)
            }

            unbookedTransactionRepository.delete(
                conn,
                unbookedTransaction.accountId,
                unbookedTransaction.id,
            )

            changelog.add(UnbookedTransactionsChanged)
            changelog.add(BookingsChanged(realmId))
        }
    }

    fun removeMatcher(
        userId: String,
        realmId: String,
        matcherId: String
    ) {
        changelog.writeTx { conn ->
            authorizationService.assertAuthorization(
                conn,
                userId,
                realmId,
                AccessRequest.write,
            )

            unbookedBankTransactionMatcherRepository.remove(
                conn,
                matcherId,
                realmId
            )

            changelog.add(UnbookedTransactionMatchersChanged)
        }
    }


    fun addOrReplaceMatcher(
        userId: String,
        realmId: String,
        matcher: UnbookedBankTransactionMatcher
    ) {
        changelog.writeTx { conn ->
            authorizationService.assertAuthorization(
                conn,
                userId,
                realmId,
                AccessRequest.write,
            )

            check(matcher.realmId == realmId)

            val updated = unbookedBankTransactionMatcherRepository.update(
                conn,
                matcher.copy(lastUsed = Instant.now())
            )
            if (!updated) {
                unbookedBankTransactionMatcherRepository.insert(
                    conn,
                    matcher.copy(lastUsed = Instant.now())
                )
            }


            changelog.add(UnbookedTransactionMatchersChanged)
        }
    }

    data class UnbookedBankTransactionReference(
        val accountId: String,
        val unbookedTransactionId: Long
    )

    fun getMatchers(
        userId: String,
        realmId: String,
        unbookedBankTransactionReference: UnbookedBankTransactionReference?,
    ): List<MatchedUnbookedBankTransactionMatcher> = dataSource.readTx { conn ->
        authorizationService.assertAuthorization(
            conn,
            userId,
            realmId,
            AccessRequest.read,
        )

        val unbookedTransaction = unbookedBankTransactionReference?.let {
            unbookedTransactionRepository.getUnbookedTransaction(
                conn,
                realmId,
                it.accountId,
                it.unbookedTransactionId
            )
        }


        unbookedBankTransactionMatcherRepository.getAll(conn, realmId).map { unbookedBankTransactionMatcher ->
            MatchedUnbookedBankTransactionMatcher(
                unbookedTransaction?.let { unbookedBankTransactionMatcher.filter.matches(it) } ?: false,
                unbookedBankTransactionMatcher
            )
        }
    }

    fun getUnbookedBankTransaction(
        userId: String,
        realmId: String,
        unbookedBankTransactionReference: UnbookedBankTransactionReference
    ) = dataSource.readTx { conn ->
        authorizationService.assertAuthorization(
            conn,
            userId,
            realmId,
            AccessRequest.read,
        )
        unbookedTransactionRepository.getUnbookedTransaction(
            conn,
            realmId,
            unbookedBankTransactionReference.accountId,
            unbookedBankTransactionReference.unbookedTransactionId
        )
    }
}

data class MatchedUnbookedBankTransactionMatcher(
    val matches: Boolean,
    val matcher: UnbookedBankTransactionMatcher,
)

