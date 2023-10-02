package com.dehnes.accounting.api

import com.dehnes.accounting.api.dtos.*
import com.dehnes.accounting.api.dtos.ReadRequestType.*
import com.dehnes.accounting.bank.TransactionMatchingService
import com.dehnes.accounting.database.BankTxDateRangeFilter
import com.dehnes.accounting.database.ChangeLogEventType
import com.dehnes.accounting.rapports.RapportLeaf
import com.dehnes.accounting.rapports.RapportRequest
import com.dehnes.accounting.rapports.RapportService
import com.dehnes.accounting.services.*
import com.dehnes.accounting.utils.wrap
import mu.KotlinLogging
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService

class ReadService(
    private val bookingReadService: BookingReadService,
    private val bankService: BankService,
    private val executorService: ExecutorService,
    private val userService: UserService,
    private val rapportService: RapportService,
    private val categoryService: CategoryService,
    private val userStateService: UserStateService,
    private val transactionMatchingService: TransactionMatchingService,
) {

    private val logger = KotlinLogging.logger { }

    private val listeners = ConcurrentHashMap<String, WebSocketServer.Subscription>()

    private val threadLocalChangeLog = ThreadLocal<Queue<ChangeEvent>>()

    fun  <T> doWithNotifies(fn: () -> T): T {
        threadLocalChangeLog.set(LinkedList())
        val result: T?
        try {
            result = fn()
        } finally {
            threadLocalChangeLog.get().forEach(::sendOutNotifies)
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

    private fun sendOutNotifies(changeEvent: ChangeEvent) {
        executorService.submit(wrap(logger) {
            listeners.values
                .filter { changeEvent.subId == null || it.subscriptionId == changeEvent.subId }
                .filter { changeEvent.changeLogEventType == null || changeEvent.changeLogEventType in it.readRequest.type.events }
                .forEach { sub ->
                    try {
                        sub.onEvent(
                            Notify(sub.subscriptionId, handleRequest(sub.userId, sub.readRequest))
                        )
                    } catch (e: Throwable) {
                        logger.error(e) { "" }
                    }
                }
        })
    }

    fun handleRequest(userId: String, readRequest: ReadRequest): ReadResponse = when (readRequest.type) {
        userInfo -> ReadResponse(userView = UserView.fromUser(userService.getUserById(userId)!!))

        userState -> ReadResponse(userState = userStateService.getUserState(userId))

        getLedgers -> ReadResponse(ledgers = bookingReadService.listLedgers(userId, false))

        allCategories -> ReadResponse(categories = categoryService.get().asList.map {
            CategoryView(it.id, it.name, it.description, it.parentCategoryId)
        })

        getBankAccounts -> ReadResponse(
            bankAccounts = bankService.getAllAccountsFor(
                userId,
                readRequest.ledgerId!!
            )
        )

        getBankTransactions -> {
            val request = readRequest.bankTransactionsRequest!!
            ReadResponse(
                bankTransactions = bankService.getTransactions(
                    userId,
                    readRequest.ledgerId!!,
                    request.bankAccountId,
                    BankTxDateRangeFilter(request.from, request.toExcluding)
                )
            )
        }

        getBankTransaction -> {
            val bankTransactionRequest = readRequest.bankTransactionRequest!!
            ReadResponse(
                bankTransaction = bankService.getTransactions(
                    userId,
                    bankTransactionRequest.ledgerId,
                    bankTransactionRequest.bankAccountId,
                    bankTransactionRequest.transactionId
                )
            )
        }

        ledgerRapport -> {

            val rapport = rapportService.rapport(
                RapportRequest(
                    readRequest.ledgerId!!,
                    readRequest.ledgerRapportRequest!!.from,
                    readRequest.ledgerRapportRequest.toExcluding,
                    emptyList()
                )
            )

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
                                    it.category.name,
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
                userId,
                matchersRequest.ledgerId,
                matchersRequest.testMatchFor
            )

            ReadResponse(getMatchersResponse = r)
        }
    }

}

data class ChangeEvent(
    val changeLogEventType: ChangeLogEventType?,
    val subId: String?,
)