package com.dehnes.accounting.api

import com.dehnes.accounting.api.dtos.*
import com.dehnes.accounting.api.dtos.ReadRequestType.*
import com.dehnes.accounting.database.*
import com.dehnes.accounting.database.Transactions.readTx
import com.dehnes.accounting.services.*
import com.dehnes.accounting.utils.wrap
import mu.KotlinLogging
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.locks.ReentrantLock
import javax.sql.DataSource
import kotlin.concurrent.withLock


class ReadService(
    private val executorService: ExecutorService,
    private val userStateService: UserStateService,
    private val realmRepository: RealmRepository,
    private val userService: UserService,
    private val dataSource: DataSource,
    private val overviewRapportService: OverviewRapportService,
    private val bankAccountService: BankAccountService,
    private val accountsRepository: AccountsRepository,
    private val unbookedBankTransactionMatcherService: UnbookedBankTransactionMatcherService,
    private val bookingService: BookingService,
    private val changelog: Changelog,
    private val databaseBackupService: DatabaseBackupService,
) {

    private val logger = KotlinLogging.logger { }

    private val readCacheLock = ReentrantLock()
    private val readCache = mutableMapOf<String, CacheItem>()

    private class CacheItem(
        var result: ReadResponse? = null,
        val lock: ReentrantLock = ReentrantLock(),
    )

    init {
        UUID.randomUUID().toString().apply {
            changelog.syncListeners[this] = { e ->
                when {
                    e.changeLogEventTypeV2 is UserStateUpdated -> {}
                    else -> {
                        invalidateCache()
                    }
                }
            }
        }
    }

    private fun invalidateCache() {
        readCacheLock.withLock {
            readCache.clear()
        }
    }

    fun addSubscription(sub: WebSocketServer.Subscription) {
        logger.info { "Added subscription id=${sub.subscriptionId}" }

        val listener = Listener(
            id = sub.subscriptionId,
            filter = { changeEvent ->
                when {
                    changeEvent.changeLogEventTypeV2 == null -> true
                    changeEvent.changeLogEventTypeV2 is DatabaseRestored -> true
                    changeEvent.changeLogEventTypeV2::class in sub.readRequest.type.listensOnV2 && changeEvent.changeLogEventTypeV2.additionalFilter(
                        sub.readRequest,
                        sub.sessionId
                    ) -> true

                    else -> false
                }
            },
            onEvent = { _ ->
                executorService.submit(wrap(logger) {
                    val readResponse = handleRequest(
                        sub.userId,
                        sub.readRequest,
                        sub.sessionId,
                    ) {
                        sub.onEvent(
                            Notify(
                                sub.subscriptionId,
                                null,
                                true
                            )
                        )
                    }

                    sub.onEvent(
                        Notify(
                            sub.subscriptionId,
                            readResponse,
                            null
                        )
                    )
                })
            }
        )
        changelog.asyncListeners[sub.subscriptionId] = listener

        executorService.submit(wrap(logger) {
            listener.onEvent(ChangeEvent(null))
        })
    }

    fun removeSubscription(subId: String) {
        logger.info { "Removed subscription id=${subId}" }
        changelog.asyncListeners.remove(subId)
    }

    fun handleRequest(
        userId: String,
        readRequest: ReadRequest,
        sessionId: String,
        sendWorkingNotify: () -> Unit
    ): ReadResponse {

        val userState = userStateService.getUserStateV2(sessionId)
        val cacheKey = "$userId $readRequest $userState"

        val cacheItem = readCacheLock.withLock {
            readCache.getOrPut(cacheKey) { CacheItem() }
        }

        cacheItem.result?.apply {
            return this
        }

        return cacheItem.lock.withLock {
            cacheItem.result ?: run {
                cacheItem.result = dataSource.readTx { conn ->
                    sendWorkingNotify()

                    handleRequestInternal(
                        userId,
                        readRequest,
                        userState
                    )
                }

                cacheItem.result!!
            }
        }
    }

    private fun handleRequestInternal(
        userId: String,
        readRequest: ReadRequest,
        userStateV2: UserStateV2,
    ): ReadResponse =
        when (readRequest.type) {
            listBackups -> ReadResponse(
                backups = databaseBackupService.listBackups()
            )

            getAllUsers -> ReadResponse(
                realms = dataSource.readTx { conn ->
                    realmRepository.getAll(conn).map {
                        RealmInfo(
                            it.id,
                            it.name,
                            it.description
                        )
                    }
                },
                allUsers = userService.getAllUsers(userId)
            )

            getUserInfo -> ReadResponse(
                userInfo = userService.getUserInfo(userId)
            )

            getBankAccount -> ReadResponse(
                bankAccount = bankAccountService.getBankAccount(
                    userId,
                    userStateV2.selectedRealm!!,
                    readRequest.accountId!!
                )
            )

            getBookings -> ReadResponse(
                bookings = bookingService.getBookings(
                    userId = userId,
                    realmId = userStateV2.selectedRealm!!,
                    bookingsFilters = listOf(
                        userStateV2.rangeFilter!!,
                        AccountIdFilter(
                            accountId = readRequest.accountId!!,
                            realmId = userStateV2.selectedRealm
                        )
                    )
                )
            )

            getTotalUnbookedTransactions -> ReadResponse(
                totalUnbookedTransactions = unbookedBankTransactionMatcherService.getTotalUnbookedTransactions(
                    userId,
                    userStateV2.selectedRealm!!
                )
            )

            getUnbookedBankTransaction -> ReadResponse(
                unbookedTransaction = unbookedBankTransactionMatcherService.getUnbookedBankTransaction(
                    userId,
                    userStateV2.selectedRealm!!,
                    readRequest.unbookedBankTransactionReference!!
                )
            )

            getUnbookedBankTransactionMatchers -> ReadResponse(
                unbookedBankTransactionMatchers = unbookedBankTransactionMatcherService.getMatchers(
                    userId,
                    userStateV2.selectedRealm!!,
                    readRequest.unbookedBankTransactionReference
                )
            )

            getAllAccounts -> ReadResponse(allAccounts = dataSource.readTx {
                accountsRepository.getAll(
                    it,
                    userStateV2.selectedRealm!!
                )
            })

            getBanksAndAccountsOverview -> ReadResponse(
                banksAndAccountsOverview = bankAccountService.getOverview(
                    userId,
                    userStateV2.selectedRealm!!
                )
            )

            getBankAccountTransactions -> ReadResponse(
                getBankAccountTransactions = bankAccountService.getBankAccountTransactions(
                    userId,
                    userStateV2.selectedRealm!!,
                    readRequest.accountId!!,
                    userStateV2.rangeFilter!!
                )
            )

            getUserState -> ReadResponse(userStateV2 = userStateV2)

            getOverviewRapport -> {
                val readResponse = ReadResponse(
                    overViewRapport = if (userStateV2.rangeFilter != null && userStateV2.selectedRealm != null) {
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
                    realmId = userStateV2.selectedRealm!!,
                    bookingId = readRequest.getBookingId!!
                )
            )


        }
}

data class ChangeEvent(
    val changeLogEventTypeV2: ChangeLogEventTypeV2?,
)

sealed class ChangeLogEventTypeV2 {
    open fun additionalFilter(readRequest: ReadRequest, sessionId: String): Boolean = true
}

data class UserStateUpdated(
    val affectedSessionsId: List<String>
) : ChangeLogEventTypeV2() {
    override fun additionalFilter(readRequest: ReadRequest, sessionId: String) = sessionId in affectedSessionsId
}

object UserUpdated : ChangeLogEventTypeV2()

object AccountsChanged : ChangeLogEventTypeV2()

data class BookingsChanged(val realmId: String) : ChangeLogEventTypeV2()

object UnbookedTransactionsChanged : ChangeLogEventTypeV2()

object UnbookedTransactionMatchersChanged : ChangeLogEventTypeV2()

object BankAccountChanged : ChangeLogEventTypeV2()

object DatabaseBackupChanged : ChangeLogEventTypeV2()
object DatabaseRestored : ChangeLogEventTypeV2()