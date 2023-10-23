package com.dehnes.accounting.api

import com.dehnes.accounting.api.dtos.Notify
import com.dehnes.accounting.api.dtos.ReadRequest
import com.dehnes.accounting.api.dtos.ReadRequestType.*
import com.dehnes.accounting.api.dtos.ReadResponse
import com.dehnes.accounting.api.dtos.UserStateV2
import com.dehnes.accounting.database.AccountIdFilter
import com.dehnes.accounting.database.AccountsRepository
import com.dehnes.accounting.database.Transactions.readTx
import com.dehnes.accounting.services.*
import com.dehnes.accounting.utils.wrap
import mu.KotlinLogging
import java.sql.Connection
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import javax.sql.DataSource

class ReadService(
    private val executorService: ExecutorService,
    private val userStateService: UserStateService,
    private val dataSource: DataSource,
    private val authorizationService: AuthorizationService,
    private val overviewRapportService: OverviewRapportService,
    private val bankAccountService: BankAccountService,
    private val accountsRepository: AccountsRepository,
    private val unbookedBankTransactionMatcherService: UnbookedBankTransactionMatcherService,
    private val bookingService: BookingService,
    private val accountService: AccountService,
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
        logger.info { "Added subscription id=${sub.subscriptionId}" }
        listeners[sub.subscriptionId] = sub
        onChangelogEvent(sub.subscriptionId)
    }

    fun removeSubscription(subId: String) {
        logger.info { "Removed subscription id=${subId}" }
        listeners.remove(subId)
    }

    fun onChangelogEvent(subId: String? = null) {
        threadLocalChangeLog.get()?.add(ChangeEvent(null, subId))
    }

    fun onChangelogEvent(changeEvent: ChangeEvent) {
        threadLocalChangeLog.get()?.add(changeEvent)
    }

    private fun sendOutNotifies(changeEvent: Collection<ChangeEvent>) {
        val compressed = changeEvent.toSet()

        compressed.forEach { changeEvent ->

            executorService.submit(wrap(logger) {


                dataSource.readTx { conn ->

                    listeners.values
                        .filter { changeEvent.subId == null || it.subscriptionId == changeEvent.subId }
                        .filter {
                            (changeEvent.changeLogEventTypeV2 == null
                                    || (changeEvent.changeLogEventTypeV2::class in it.readRequest.type.listensOnV2
                                    && changeEvent.changeLogEventTypeV2.triggerNotify(it.readRequest, it.sessionId)))
                        }
                        .forEach { sub ->


                            val userState = userStateService.getUserStateV2(conn, sub.sessionId)

                            try {
                                sub.onEvent(
                                    Notify(
                                        sub.subscriptionId,
                                        handleRequest(
                                            conn,
                                            sub.userId,
                                            sub.readRequest,
                                            userState,
                                        )
                                    )
                                )
                            } catch (e: Throwable) {
                                logger.error(e) { "" }
                            }

                        }




                }



            })
        }
    }

    fun handleRequest(
        connection: Connection,
        userId: String,
        readRequest: ReadRequest,
        userStateV2: UserStateV2?,
    ): ReadResponse =
        when (readRequest.type) {

            getBookings -> ReadResponse(
                bookings = bookingService.getBookings(
                    userId = userId,
                    realmId = userStateV2!!.selectedRealm!!,
                    bookingsFilters = listOf(
                        userStateV2.rangeFilter!!,
                        AccountIdFilter(readRequest.accountId!!, userStateV2.selectedRealm!!)
                    )
                )
            )

            getTotalUnbookedTransactions -> ReadResponse(
                totalUnbookedTransactions = unbookedBankTransactionMatcherService.getTotalUnbookedTransactions(
                    userId,
                    userStateV2!!.selectedRealm!!
                )
            )

            getUnbookedBankTransaction -> ReadResponse(
                unbookedTransaction = unbookedBankTransactionMatcherService.getUnbookedBankTransaction(
                    userId,
                    userStateV2!!.selectedRealm!!,
                    readRequest.unbookedBankTransactionReference!!
                )
            )

            getUnbookedBankTransactionMatchers -> ReadResponse(
                unbookedBankTransactionMatchers = unbookedBankTransactionMatcherService.getMatchers(
                    userId,
                    userStateV2!!.selectedRealm!!,
                    readRequest.unbookedBankTransactionReference
                )
            )

            getAllAccounts -> ReadResponse(allAccounts = dataSource.readTx {
                accountsRepository.getAll(
                    it,
                    userStateV2!!.selectedRealm!!
                )
            })

            getBanksAndAccountsOverview -> ReadResponse(
                banksAndAccountsOverview = bankAccountService.getOverview(
                    userId,
                    userStateV2!!.selectedRealm!!
                )
            )

            getBankAccountTransactions -> ReadResponse(
                getBankAccountTransactions = bankAccountService.getBankAccountTransactions(
                    userId,
                    userStateV2!!.selectedRealm!!,
                    readRequest.accountId!!,
                    userStateV2.rangeFilter!!
                )
            )

            getAllRealms -> ReadResponse(
                realms = authorizationService.getAuthorizedRealms(
                    connection,
                    userId,
                    AccessRequest.read
                )
            )

            getUserState -> ReadResponse(userStateV2 = userStateV2)

            getOverviewRapport -> {
                val readResponse = ReadResponse(
                    overViewRapport = if (userStateV2?.rangeFilter != null && userStateV2.selectedRealm != null) {
                        overviewRapportService.createRapport(
                            userStateV2.selectedRealm,
                            userStateV2.rangeFilter
                        )
                    } else emptyList()
                )
                readResponse
            }

            getBooking -> ReadResponse(
                booking = bookingService.getBooking(
                    userId = userId,
                    realmId = userStateV2!!.selectedRealm!!,
                    bookingId = readRequest.getBookingId!!
                )
            )


        }
}

data class ChangeEvent(
    val changeLogEventTypeV2: ChangeLogEventTypeV2?,
    val subId: String?,
)

sealed class ChangeLogEventTypeV2 {
    abstract fun triggerNotify(readRequest: ReadRequest, sessionId: String): Boolean
}

data class UserStateUpdated(
    val affectedSessionsId: List<String>
) : ChangeLogEventTypeV2() {
    override fun triggerNotify(readRequest: ReadRequest, sessionId: String) = sessionId in affectedSessionsId
}

data class RealmChanged(
    val realmId: String
) : ChangeLogEventTypeV2() {
    override fun triggerNotify(readRequest: ReadRequest, sessionId: String) = readRequest.type == getAllRealms
}

object AccountsChanged : ChangeLogEventTypeV2() {
    override fun triggerNotify(readRequest: ReadRequest, sessionId: String) = true
}

object BookingsChanged : ChangeLogEventTypeV2() {
    override fun triggerNotify(readRequest: ReadRequest, sessionId: String) = true
}

object PartiesChanged : ChangeLogEventTypeV2() {
    override fun triggerNotify(readRequest: ReadRequest, sessionId: String) = true
}

object UnbookedTransactionsChanged : ChangeLogEventTypeV2() {
    override fun triggerNotify(readRequest: ReadRequest, sessionId: String) = true
}

object UnbookedTransactionMatchersChanged : ChangeLogEventTypeV2() {
    override fun triggerNotify(readRequest: ReadRequest, sessionId: String) = true
}

