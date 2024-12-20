package com.dehnes.accounting.api

import com.dehnes.accounting.Configuration
import com.dehnes.accounting.api.dtos.*
import com.dehnes.accounting.api.dtos.RequestType.*
import com.dehnes.accounting.bank.importers.BankTransactionImportService
import com.dehnes.accounting.database.DatabaseBackupService
import com.dehnes.accounting.database.RealmRepository
import com.dehnes.accounting.services.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.websocket.CloseReason
import jakarta.websocket.Endpoint
import jakarta.websocket.EndpointConfig
import jakarta.websocket.Session
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.util.*

// one instance per sessions
class WebSocketServer : Endpoint() {

    private val instanceId = UUID.randomUUID().toString()

    private val objectMapper = Configuration.getBean<ObjectMapper>()
    private val userService = Configuration.getBean<UserService>()
    private val readService = Configuration.getBean<ReadService>()
    private val bankAccountService = Configuration.getBean<BankAccountService>()
    private val userStateService = Configuration.getBean<UserStateService>()
    private val bookingService = Configuration.getBean<BookingService>()
    private val accountService = Configuration.getBean<AccountService>()
    private val batabaseBackupService = Configuration.getBean<DatabaseBackupService>()
    private val bankTransactionImportService = Configuration.getBean<BankTransactionImportService>()
    private val budgetService = Configuration.getBean<BudgetService>()
    private val unbookedBankTransactionMatcherService = Configuration.getBean<UnbookedBankTransactionMatcherService>()
    private val realmService = Configuration.getBean<RealmService>()
    private val logger = KotlinLogging.logger { }
    private val subscriptions = mutableMapOf<String, Subscription>()

    override fun onOpen(sess: Session, p1: EndpointConfig?) {
        logger.info { "$instanceId Socket connected: $sess" }

        sess.addMessageHandler(String::class.java) { msg -> onWebSocketText(sess, msg) }
    }

    override fun onClose(session: Session, closeReason: CloseReason) {
        subscriptions.values.toList().forEach { it.close() }
        logger.info { "$instanceId Socket Closed: $closeReason" }
    }

    override fun onError(session: Session?, cause: Throwable?) {
        logger.warn(cause) { "$instanceId " }
    }

    fun onWebSocketText(argSession: Session, argMessage: String) {
        val userEmail = (argSession.userProperties["userEmail"] as String?) ?: error("user missing")
        val user = userService.getUserByEmail(userEmail) ?: error("No user found with $userEmail")
        val userStateV2 = userStateService.getUserStateV2(user.id)

        val websocketMessage: WebsocketMessage = objectMapper.readValue(argMessage)

        if (websocketMessage.type != WebsocketMessageType.rpcRequest) {
            return
        }

        val rpcRequest = websocketMessage.rpcRequest!!


        val (response, error) = logAndGetError(logger) {
            when (rpcRequest.type) {
                subscribe -> {
                    val subscribe = rpcRequest.subscribe!!
                    val subscriptionId = subscribe.subscriptionId

                    synchronized(subscriptions) {
                        subscriptions[subscriptionId]?.close()
                        val sub = Subscription(subscriptionId, argSession, user.id, subscribe.readRequest)
                        subscriptions[subscriptionId] = sub
                        readService.addSubscription(sub)
                    }

                    RpcResponse(subscriptionCreated = true)
                }

                unsubscribe -> {
                    val subscriptionId = rpcRequest.unsubscribe!!.subscriptionId
                    subscriptions.remove(subscriptionId)?.close()
                    logger.info { "$instanceId Removed subscription id=$subscriptionId" }
                    RpcResponse(subscriptionRemoved = true)
                }

                closeNextMonth -> {
                    realmService.updateClosure(
                        user.id,
                        userStateV2.selectedRealm!!,
                        RealmRepository.CloseDirection.forward
                    )
                    RpcResponse()
                }

                reopenPreviousMonth -> {
                    realmService.updateClosure(
                        user.id,
                        userStateV2.selectedRealm!!,
                        RealmRepository.CloseDirection.backwards
                    )
                    RpcResponse()
                }

                createNewBackup -> {
                    batabaseBackupService.createBackup()
                    RpcResponse()
                }

                dropBackup -> {
                    batabaseBackupService.dropBackup(rpcRequest.backupName!!)
                    RpcResponse()
                }

                restoreBackup -> {
                    batabaseBackupService.restoreBackup(rpcRequest.backupName!!)
                    RpcResponse()
                }

                addOrReplaceUser -> {
                    userService.addOrReplaceUser(
                        user.id,
                        rpcRequest.user!!
                    )
                    RpcResponse()
                }

                deleteUser -> {
                    userService.deleteUser(
                        user.id,
                        rpcRequest.deleteUserId!!
                    )
                    RpcResponse()
                }

                deleteBankAccount -> {
                    bankAccountService.deleteBankAccount(
                        user.id,
                        userStateV2.selectedRealm!!,
                        rpcRequest.accountId!!
                    )
                    RpcResponse()
                }

                createOrUpdateBankAccount -> {
                    bankAccountService.createOrUpdateBankAccount(
                        user.id,
                        userStateV2.selectedRealm!!,
                        rpcRequest.bankAccount!!
                    )
                    RpcResponse()
                }

                createOrUpdateAccount -> {
                    accountService.createOrUpdateAccount(
                        user.id,
                        userStateV2.selectedRealm!!,
                        rpcRequest.createOrUpdateAccount!!
                    )
                    RpcResponse()
                }

                mergeAccount -> {
                    accountService.merge(
                        user.id,
                        userStateV2.selectedRealm!!,
                        rpcRequest.accountId!!,
                        rpcRequest.mergeTargetAccountId!!
                    )
                    RpcResponse()
                }

                createOrUpdateBooking -> {
                    val id = bookingService.createOrUpdateBooking(
                        user.id,
                        userStateV2.selectedRealm!!,
                        rpcRequest.createOrUpdateBooking!!
                    )
                    RpcResponse(editedBookingId = id)
                }

                deleteBooking -> {
                    bookingService.deleteBooking(
                        user.id,
                        userStateV2.selectedRealm!!,
                        rpcRequest.bookingId!!
                    )
                    RpcResponse()
                }

                updateChecked -> {
                    bookingService.updateChecked(
                        userStateV2.selectedRealm!!,
                        user.id,
                        rpcRequest.bookingId!!,
                        rpcRequest.bookingEntryId!!,
                        rpcRequest.bookingEntryChecked!!
                    )
                    RpcResponse()
                }

                executeMatcherUnbookedTransactionMatcher -> {
                    unbookedBankTransactionMatcherService.executeMatcher(
                        user.id,
                        userStateV2.selectedRealm!!,
                        rpcRequest.executeMatcherRequest!!
                    )
                    RpcResponse()
                }

                removeUnbookedTransactionMatcher -> {
                    unbookedBankTransactionMatcherService.removeMatcher(
                        user.id,
                        userStateV2.selectedRealm!!,
                        rpcRequest.removeUnbookedTransactionMatcherId!!
                    )
                    RpcResponse()
                }

                addOrReplaceUnbookedTransactionMatcher -> {
                    unbookedBankTransactionMatcherService.addOrReplaceMatcher(
                        user.id,
                        userStateV2.selectedRealm!!,
                        rpcRequest.unbookedBankTransactionMatcher!!
                    )
                    RpcResponse()
                }

                deleteAllUnbookedTransactions -> {
                    bankAccountService.deleteAllUnbookedTransactions(
                        user.id,
                        userStateV2.selectedRealm!!,
                        rpcRequest.accountId!!
                    )
                    RpcResponse()
                }
                deleteUnbookedTransaction -> {
                    bankAccountService.deleteUnbookedTransaction(
                        user.id,
                        userStateV2.selectedRealm!!,
                        rpcRequest.accountId!!,
                        rpcRequest.deleteUnbookedBankTransactionId!!,
                    )
                    RpcResponse()
                }

                setUserStateV2 -> {
                    check(rpcRequest.userStateV2!!.id == userStateV2.id)
                    userStateService.setUserStateV2(user.id, rpcRequest.userStateV2)
                    RpcResponse()
                }

                importBankTransactions -> {
                    val request = rpcRequest.importBankTransactionsRequest!!

                    RpcResponse(
                        importBankTransactionsResult = bankTransactionImportService.doImport(
                            user.id,
                            userStateV2.selectedRealm!!,
                            request.accountId,
                            ByteArrayInputStream(Base64.getDecoder().decode(request.dataBase64)),
                            request.filename,
                        )
                    )
                }

                updateBudgetRulesForAccount -> {
                    budgetService.updateBudgetRulesForAccount(
                        user.id,
                        userStateV2.selectedRealm!!,
                        rpcRequest.updateBudget!!,
                    )
                    RpcResponse()
                }

            }
        }

        argSession.basicRemote.sendText(
            objectMapper.writeValueAsString(
                WebsocketMessage(
                    websocketMessage.id,
                    WebsocketMessageType.rpcResponse,
                    null,
                    if (error != null) {
                        RpcResponse(error = error)
                    } else {
                        response
                    },
                    null
                )
            )
        )
    }

    inner class Subscription(
        val subscriptionId: String,
        val sess: Session,
        val userId: String,
        val readRequest: ReadRequest,
    ) : Closeable {
        fun onEvent(n: Notify) {
            sess.basicRemote.sendText(
                objectMapper.writeValueAsString(
                    WebsocketMessage(
                        UUID.randomUUID().toString(),
                        WebsocketMessageType.notify,
                        notify = n
                    )
                )
            )
        }

        override fun close() {
            readService.removeSubscription(subscriptionId)
            subscriptions.remove(subscriptionId)
        }
    }


}

fun <T> logAndGetError(logger: KLogger, fn: () -> T) = try {
    fn() to null
} catch (e: Throwable) {
    logger.warn(e) { "" }
    null to e.localizedMessage
}