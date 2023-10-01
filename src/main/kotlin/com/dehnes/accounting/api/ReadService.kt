package com.dehnes.accounting.api

import com.dehnes.accounting.api.dtos.*
import com.dehnes.accounting.api.dtos.ReadRequestType.*
import com.dehnes.accounting.database.BankTxDateRangeFilter
import com.dehnes.accounting.database.ChangeLogEventType
import com.dehnes.accounting.rapports.RapportLeaf
import com.dehnes.accounting.rapports.RapportRequest
import com.dehnes.accounting.rapports.RapportService
import com.dehnes.accounting.services.BankService
import com.dehnes.accounting.services.BookingReadService
import com.dehnes.accounting.services.UserService
import com.dehnes.accounting.utils.wrap
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService

class ReadService(
    private val bookingReadService: BookingReadService,
    private val bankService: BankService,
    private val executorService: ExecutorService,
    private val userService: UserService,
    private val rapportService: RapportService,
) {

    private val logger = KotlinLogging.logger { }

    private val listeners = ConcurrentHashMap<String, WebSocketServer.Subscription>()

    fun addSubscription(sub: WebSocketServer.Subscription) {
        listeners[sub.subscriptionId] = sub
        executorService.submit(wrap(logger) {
            onChangelogEvent(null, sub.subscriptionId)
        })
    }

    fun removeSubscription(subId: String) {
        listeners.remove(subId)
    }

    fun onChangelogEvent(changeLogEventType: ChangeLogEventType?, subId: String? = null) {
        listeners.values
            .filter { subId == null || it.subscriptionId == subId }
            .filter { changeLogEventType == null || changeLogEventType in it.readRequest.type.events }
            .forEach { sub ->
                try {
                    sub.onEvent(
                        Notify(sub.subscriptionId, handleRequest(sub.userId, sub.readRequest))
                    )
                } catch (e: Throwable) {
                    logger.error(e) { "" }
                }
            }
    }

    fun handleRequest(userId: String, readRequest: ReadRequest): ReadResponse = when (readRequest.type) {
        userInfo -> ReadResponse(userView = UserView.fromUser(userService.getUserById(userId)!!))

        getLedgers -> ReadResponse(ledgers = bookingReadService.listLedgers(userId))

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
    }

}