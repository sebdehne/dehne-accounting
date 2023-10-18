package com.dehnes.accounting.api

import com.dehnes.accounting.api.dtos.*
import com.dehnes.accounting.api.dtos.RequestType.*
import com.dehnes.accounting.services.TransactionMatchingService
import com.dehnes.accounting.bank.importers.BankTransactionImportService
import com.dehnes.accounting.configuration
import com.dehnes.accounting.services.*
import com.dehnes.accounting.services.bank.BankAccountService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.websocket.CloseReason
import jakarta.websocket.Endpoint
import jakarta.websocket.EndpointConfig
import jakarta.websocket.Session
import mu.KLogger
import mu.KotlinLogging
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.util.*

// one instance per sessions
class WebSocketServer : Endpoint() {

    private val instanceId = UUID.randomUUID().toString()

    private val objectMapper = configuration.getBean<ObjectMapper>()
    private val userService = configuration.getBean<UserService>()
    private val readService = configuration.getBean<ReadService>()
    private val bankService = configuration.getBean<BankService>()
    private val bankAccountService = configuration.getBean<BankAccountService>()
    private val categoryWriteService = configuration.getBean<CategoryWriteService>()
    private val userStateService = configuration.getBean<UserStateService>()
    private val transactionMatchingService = configuration.getBean<TransactionMatchingService>()
    private val bankTransactionImportService = configuration.getBean<BankTransactionImportService>()
    private val bookingWriteService = configuration.getBean<BookingWriteService>()
    private val unbookedBankTransactionMatcherService = configuration.getBean<UnbookedBankTransactionMatcherService>()
    private val logger = KotlinLogging.logger { }
    private val subscriptions = mutableMapOf<String, Subscription>()

    override fun onOpen(sess: Session, p1: EndpointConfig?) {
        logger.info("$instanceId Socket connected: $sess")

        sess.addMessageHandler(String::class.java) { msg -> onWebSocketText(sess, msg) }
    }

    override fun onClose(session: Session, closeReason: CloseReason) {
        subscriptions.values.toList().forEach { it.close() }
        logger.info("$instanceId Socket Closed: $closeReason")
    }

    override fun onError(session: Session?, cause: Throwable?) {
        logger.warn("$instanceId ", cause)
    }

    fun onWebSocketText(argSession: Session, argMessage: String) {
        val userEmail = (argSession.userProperties["userEmail"] as String?) ?: error("user missing")
        val sessionId = (argSession.userProperties["sessionId"] as String?) ?: error("sessionId missing")
        val user = userService.getUserByEmail(userEmail) ?: error("No user found with $userEmail")
        val userStateV2 = userStateService.getUserStateV2(sessionId)

        val websocketMessage: WebsocketMessage = objectMapper.readValue(argMessage)

        if (websocketMessage.type != WebsocketMessageType.rpcRequest) {
            return
        }

        val rpcRequest = websocketMessage.rpcRequest!!
        val response: RpcResponse = when (rpcRequest.type) {
            subscribe -> readService.doWithNotifies {
                val subscribe = rpcRequest.subscribe!!
                val subscriptionId = subscribe.subscriptionId

                synchronized(subscriptions) {
                    subscriptions[subscriptionId]?.close()
                    val sub = Subscription(subscriptionId, argSession, user.id, subscribe.readRequest, sessionId)
                    subscriptions[subscriptionId] = sub
                    readService.addSubscription(sub)
                }

                logger.info { "$instanceId Added subscription id=$subscriptionId" }

                RpcResponse(subscriptionCreated = true)
            }

            unsubscribe -> {
                val subscriptionId = rpcRequest.unsubscribe!!.subscriptionId
                subscriptions.remove(subscriptionId)?.close()
                logger.info { "$instanceId Removed subscription id=$subscriptionId" }
                RpcResponse(subscriptionRemoved = true)
            }

            executeMatcherUnbookedTransactionMatcher -> readService.doWithNotifies {
                unbookedBankTransactionMatcherService.executeMatcher(
                    user.id,
                    userStateV2.selectedRealm!!,
                    rpcRequest.executeMatcherRequest!!
                )
                RpcResponse()
            }

            removeUnbookedTransactionMatcher -> readService.doWithNotifies {
                unbookedBankTransactionMatcherService.removeMatcher(
                    user.id,
                    userStateV2.selectedRealm!!,
                    rpcRequest.removeUnbookedTransactionMatcherId!!
                )
                RpcResponse()
            }

            addOrReplaceUnbookedTransactionMatcher -> readService.doWithNotifies {
                unbookedBankTransactionMatcherService.addOrReplaceMatcher(
                    user.id,
                    userStateV2.selectedRealm!!,
                    rpcRequest.unbookedBankTransactionMatcher!!
                )
                RpcResponse()
            }

            deleteAllUnbookedTransactions -> readService.doWithNotifies {
                bankAccountService.deleteAllUnbookedTransactions(user.id, userStateV2.selectedRealm!!, rpcRequest.accountId!!)
                RpcResponse()
            }

            setUserStateV2 -> readService.doWithNotifies {
                check(rpcRequest.userStateV2!!.id == userStateV2.id)
                userStateService.setUserStateV2(user.id, rpcRequest.userStateV2)
                RpcResponse()
            }

            setUserState -> readService.doWithNotifies {
                userStateService.setUserState(user.id, rpcRequest.userState!!)
                RpcResponse()
            }

            importBankTransactions -> readService.doWithNotifies {
                val request = rpcRequest.importBankTransactionsRequest!!

                val (result, errorMsg) = logAndGetError(logger) {
                    bankTransactionImportService.doImport(
                        user.id,
                        userStateV2.selectedRealm!!,
                        request.accountId,
                        ByteArrayInputStream(Base64.getDecoder().decode(request.dataBase64)),
                        request.filename,
                        request.duplicationHandlerType.duplicationHandler
                    )
                }

                RpcResponse(importBankTransactionsResult = result, error = errorMsg)
            }

            addOrReplaceMatcher -> readService.doWithNotifies {
                val (_, errorMsg) = logAndGetError(logger) {
                    transactionMatchingService.addOrReplaceMatcher(
                        user.id,
                        rpcRequest.addOrReplaceMatcherRequest!!
                    )
                }

                RpcResponse(error = errorMsg)
            }

            deleteMatcher -> readService.doWithNotifies {
                transactionMatchingService.deleteMatcher(
                    userId = user.id,
                    ledgerId = rpcRequest.ledgerId!!,
                    matcherId = rpcRequest.deleteMatcherId!!
                )
                RpcResponse()
            }

            executeMatcher -> error("Removed V1")

            removeBooking -> readService.doWithNotifies {
                bookingWriteService.removeLast(
                    user.id,
                    rpcRequest.ledgerId!!,
                    rpcRequest.bookingId!!
                )

                RpcResponse()
            }

            removeLastBankTransaction -> readService.doWithNotifies {
                val (_, errorMsg) = logAndGetError(logger) {
                    bankService.removeLastBankTransactions(
                        user.id,
                        rpcRequest.ledgerId!!,
                        rpcRequest.accountId!!
                    )
                }
                RpcResponse(error = errorMsg)
            }

            addOrReplaceCategory -> readService.doWithNotifies {
                val (_, errorMsg) = logAndGetError(logger) {
                    categoryWriteService.addOrReplaceCategory(user.id, rpcRequest.addOrReplaceCategory!!)
                }
                RpcResponse(error = errorMsg)
            }

            mergeCategories -> readService.doWithNotifies {
                val (_, errorMsg) = logAndGetError(logger) {
                    bookingWriteService.mergeCategories(
                        user.id,
                        rpcRequest.ledgerId!!,
                        rpcRequest.mergeCategoriesRequest!!.sourceCategoryId,
                        rpcRequest.mergeCategoriesRequest.destinationCategoryId,
                    )
                }
                RpcResponse(error = errorMsg)
            }

            addOrReplaceBooking -> readService.doWithNotifies {
                val (_, errorMsg) = logAndGetError(logger) {
                    bookingWriteService.addOrReplaceBooking(
                        user.id,
                        rpcRequest.addOrReplaceBooking!!
                    )
                }
                RpcResponse(error = errorMsg)
            }
        }

        argSession.basicRemote.sendText(
            objectMapper.writeValueAsString(
                WebsocketMessage(
                    websocketMessage.id,
                    WebsocketMessageType.rpcResponse,
                    null,
                    response,
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
        val sessionId: String,
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