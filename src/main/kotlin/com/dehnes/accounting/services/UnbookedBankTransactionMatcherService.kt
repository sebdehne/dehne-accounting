package com.dehnes.accounting.services

import com.dehnes.accounting.api.BookingsChanged
import com.dehnes.accounting.api.UnbookedTransactionMatchersChanged
import com.dehnes.accounting.api.UnbookedTransactionsChanged
import com.dehnes.accounting.api.dtos.ExecuteMatcherRequest
import com.dehnes.accounting.database.*
import com.dehnes.accounting.database.Transactions.readTx
import com.dehnes.accounting.database.Transactions.writeTx
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

    fun executeMatcher(
        userId: String,
        realmId: String,
        executeMatcherRequest: ExecuteMatcherRequest,
    ) {
        dataSource.writeTx { conn ->
            authorizationService.assertAuthorization(conn, userId, realmId, AccessRequest.write)

            val unbookedTransaction = unbookedTransactionRepository.getUnbookedTransaction(
                conn,
                realmId,
                executeMatcherRequest.accountId,
                executeMatcherRequest.transactionId
            )

            val transactionMatcher =
                unbookedBankTransactionMatcherRepository.getAll(conn, realmId)
                    .single { m -> m.id == executeMatcherRequest.matcherId }


            check(transactionMatcher.filter.matches(unbookedTransaction))

            val memo = executeMatcherRequest.overrideMemo?.ifBlank { null }
                ?: transactionMatcher.actionMemo?.ifBlank { null }

            val bookings: List<AddBooking> = when (transactionMatcher.action) {
                is TransferAction -> {
                    listOf(
                        AddBooking(
                            realmId,
                            memo,
                            unbookedTransaction.datetime,
                            listOf(
                                AddBookingEntry(
                                    null,
                                    unbookedTransaction.accountId,
                                    unbookedTransaction.amountInCents
                                ),
                                AddBookingEntry(
                                    null,
                                    transactionMatcher.actionAccountId,
                                    unbookedTransaction.amountInCents * -1
                                )
                            )
                        )
                    )
                }

                is AccountAction -> {

                    val splits = mutableListOf<AddBookingEntry>()
                    val remaining =
                        transactionMatcher.action.additionalSplits.entries.fold(unbookedTransaction.amountInCents * -1) { acc, entry ->
                            splits.add(
                                AddBookingEntry(
                                    null,
                                    entry.key,
                                    entry.value
                                )
                            )
                            acc + entry.value
                        }
                    splits.add(
                        AddBookingEntry(
                            null,
                            transactionMatcher.action.mainAccountId,
                            remaining
                        )
                    )

                    listOf(
                        // payable/receivable
                        AddBooking(
                            realmId,
                            memo,
                            unbookedTransaction.datetime,
                            listOf(
                                AddBookingEntry(
                                    null,
                                    unbookedTransaction.accountId,
                                    unbookedTransaction.amountInCents
                                ),
                                AddBookingEntry(
                                    null,
                                    transactionMatcher.actionAccountId,
                                    unbookedTransaction.amountInCents * -1
                                )
                            )
                        ),


                        // income/expense
                        AddBooking(
                            realmId,
                            memo,
                            unbookedTransaction.datetime,
                            listOf(
                                AddBookingEntry(
                                    null,
                                    transactionMatcher.actionAccountId,
                                    unbookedTransaction.amountInCents
                                ),
                            ) + splits
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

            changelog.addV2(UnbookedTransactionsChanged)
            changelog.addV2(BookingsChanged)
        }
    }

    fun removeMatcher(
        userId: String,
        realmId: String,
        matcherId: String
    ) {
        dataSource.writeTx { conn ->
            authorizationService.assertAuthorization(conn, userId, realmId, AccessRequest.write)

            unbookedBankTransactionMatcherRepository.remove(
                conn,
                matcherId,
                realmId
            )

            changelog.addV2(UnbookedTransactionMatchersChanged)
        }
    }


    fun addOrReplaceMatcher(
        userId: String,
        realmId: String,
        matcher: UnbookedBankTransactionMatcher
    ) {
        dataSource.writeTx { conn ->
            authorizationService.assertAuthorization(conn, userId, realmId, AccessRequest.write)

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


            changelog.addV2(UnbookedTransactionMatchersChanged)
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
        authorizationService.assertAuthorization(conn, userId, realmId, AccessRequest.read)

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
        authorizationService.assertAuthorization(conn, userId, realmId, AccessRequest.read)
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

