package com.dehnes.accounting.api

import com.dehnes.accounting.api.dtos.*
import com.dehnes.accounting.api.dtos.ReadRequestType.*
import com.dehnes.accounting.services.TransactionMatchingService
import com.dehnes.accounting.database.AccessRequest
import com.dehnes.accounting.database.BankTxDateRangeFilter
import com.dehnes.accounting.database.ChangeLogEventType
import com.dehnes.accounting.database.DateRangeFilter
import com.dehnes.accounting.database.Transactions.readTx
import com.dehnes.accounting.rapports.RapportLeaf
import com.dehnes.accounting.rapports.RapportRequest
import com.dehnes.accounting.rapports.RapportService
import com.dehnes.accounting.services.*
import com.dehnes.accounting.utils.wrap
import mu.KotlinLogging
import java.sql.Connection
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import javax.sql.DataSource

class ReadService(
    private val bookingReadService: BookingReadService,
    private val bankService: BankService,
    private val executorService: ExecutorService,
    private val userService: UserService,
    private val rapportService: RapportService,
    private val categoryReadService: CategoryReadService,
    private val userStateService: UserStateService,
    private val transactionMatchingService: TransactionMatchingService,
    private val dataSource: DataSource,
) {

    private val logger = KotlinLogging.logger { }

    private val listeners = ConcurrentHashMap<String, WebSocketServer.Subscription>()

    private val threadLocalChangeLog = ThreadLocal<Queue<ChangeEvent>>()

    fun <T> doWithNotifies(fn: () -> T): T {
        threadLocalChangeLog.set(LinkedList())
        val result: T?
        try {
            result = fn()
        } finally {
            sendOutNotifies(threadLocalChangeLog.get())
            threadLocalChangeLog.remove()
        }

        return result!!
    }

    fun addSubscription(sub: WebSocketServer.Subscription) {
        listeners[sub.subscriptionId] = sub
        onChangelogEvent(null, sub.subscriptionId)
    }

    fun removeSubscription(subId: String) {
        listeners.remove(subId)
    }

    fun onChangelogEvent(changeLogEventType: ChangeLogEventType?, subId: String? = null) {
        threadLocalChangeLog.get()?.add(ChangeEvent(changeLogEventType, subId))
    }

    private fun sendOutNotifies(changeEvent: Collection<ChangeEvent>) {
        val compressed = changeEvent.toSet()

        executorService.submit(wrap(logger) {

            dataSource.readTx { conn ->
                compressed.forEach { changeEvent ->

                    listeners.values
                        .filter { changeEvent.subId == null || it.subscriptionId == changeEvent.subId }
                        .filter { changeEvent.changeLogEventType == null || changeEvent.changeLogEventType in it.readRequest.type.events }
                        .forEach { sub ->
                            try {
                                sub.onEvent(
                                    Notify(
                                        sub.subscriptionId,
                                        handleRequest(conn, sub.userId, sub.readRequest)
                                    )
                                )
                            } catch (e: Throwable) {
                                logger.error(e) { "" }
                            }
                        }

                }
            }


        })
    }

    fun handleRequest(
        connection: Connection,
        userId: String,
        readRequest: ReadRequest
    ): ReadResponse =
        when (readRequest.type) {
            userInfo -> ReadResponse(userView = UserView.fromUser(userService.getUserById(connection, userId)!!))

            userState -> {
                val userState = userStateService.getUserState(connection, userId)
                ReadResponse(userState = userState)
            }

            getLedgers -> ReadResponse(ledgers = bookingReadService.listLedgers(connection, userId, AccessRequest.read))

            allCategories -> ReadResponse(categories = categoryReadService.get(connection, readRequest.ledgerId!!).asList)

            getBankAccounts -> ReadResponse(
                bankAccounts = bankService.getAllAccountsFor(
                    connection,
                    userId,
                    readRequest.ledgerId!!
                )
            )

            getBankTransactions -> {
                val request = readRequest.bankTransactionsRequest!!
                val list = bankService.getTransactions(
                    connection,
                    userId,
                    readRequest.ledgerId!!,
                    request.bankAccountId,
                    BankTxDateRangeFilter(request.from, request.toExcluding)
                )
                val totalUnmatched = bankService.getTotalUnmatched(
                    connection,
                    userId,
                    readRequest.ledgerId,
                    request.bankAccountId
                )

                ReadResponse(
                    bankTransactions = BankTransactionsResponse(
                        totalUnmatched,
                        list
                    )
                )
            }

            getBankTransaction -> {
                val bankTransactionRequest = readRequest.bankTransactionRequest!!
                ReadResponse(
                    bankTransaction = bankService.getTransaction(
                        connection,
                        userId,
                        bankTransactionRequest.ledgerId,
                        bankTransactionRequest.bankAccountId,
                        bankTransactionRequest.transactionId
                    )
                )
            }

            ledgerRapport -> {

                val rapport = rapportService.rapport(
                    connection,
                    RapportRequest(
                        readRequest.ledgerId!!,
                        readRequest.ledgerRapportRequest!!.from,
                        readRequest.ledgerRapportRequest.toExcluding,
                    )
                )

                val categories = categoryReadService.get(readRequest.ledgerId)

                fun mapLeaf(rapportLeaf: RapportLeaf): LedgerRapportNode {
                    return LedgerRapportNode(
                        rapportLeaf.categoryDto.name,
                        rapportLeaf.totalAmountInCents,
                        rapportLeaf.records.map { r ->
                            LedgerRapportBookingRecord(
                                r.booking.id,
                                r.bookingRecordId,
                                r.booking.datetime,
                                r.amount(),
                                r.description(),
                                r.booking.records.filterNot { it.id == r.bookingRecordId }.map {
                                    LedgerRapportBookingContraRecord(
                                        categories.asList.first { c -> c.id == it.categoryId }.name,
                                        it.bookingId,
                                        it.id
                                    )
                                }
                            )
                        },
                        rapportLeaf.children.map { mapLeaf(it) }
                    )
                }

                ReadResponse(ledgerRapport = rapport.map { mapLeaf(it) })
            }

            getMatchers -> {

                val matchersRequest = readRequest.getMatchersRequest!!
                val r = transactionMatchingService.getMatchers(
                    connection,
                    userId,
                    matchersRequest.ledgerId,
                    matchersRequest.testMatchFor
                )

                ReadResponse(getMatchersResponse = r)
            }

            getBookings -> {
                val bookingsRequest = readRequest.getBookingsRequest!!
                val r = bookingReadService.getBookings(
                    connection,
                    userId,
                    readRequest.ledgerId!!,
                    bookingsRequest.limit ?: 1000,
                    DateRangeFilter(
                        bookingsRequest.from,
                        bookingsRequest.toExcluding
                    )
                )
                ReadResponse(getBookingsResponse = r)
            }

            getBooking -> ReadResponse(getBookingResponse = bookingReadService.getBooking(
                connection,
                readRequest.ledgerId!!,
                readRequest.getBookingId!!
            ))
        }

}

data class ChangeEvent(
    val changeLogEventType: ChangeLogEventType?,
    val subId: String?,
)