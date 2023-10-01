package com.dehnes.accounting.api

import com.dehnes.accounting.api.dtos.*
import com.dehnes.accounting.api.dtos.RequestType.*
import com.dehnes.accounting.bank.TransactionMatchingService
import com.dehnes.accounting.bank.importers.BankTransactionImportService
import com.dehnes.accounting.configuration
import com.dehnes.accounting.services.UserService
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
    private val transactionMatchingService = configuration.getBean<TransactionMatchingService>()
    private val bankTransactionImportService = configuration.getBean<BankTransactionImportService>()
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

        val websocketMessage: WebsocketMessage = objectMapper.readValue(argMessage)

        if (websocketMessage.type != WebsocketMessageType.rpcRequest) {
            return
        }

        val rpcRequest = websocketMessage.rpcRequest!!
        val response: RpcResponse = when (rpcRequest.type) {
            subscribe -> {
                val subscribe = rpcRequest.subscribe!!
                val subscriptionId = subscribe.subscriptionId

                synchronized(subscriptions) {
                    subscriptions[subscriptionId]?.close()
                    val userByEmail = userService.getUserByEmail(userEmail) ?: error("No user found with $userEmail")
                    val sub = Subscription(subscriptionId, argSession, userByEmail.id, subscribe.readRequest)
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

            importBankTransactions -> {
                val request = rpcRequest.importBankTransactionsRequest!!
                val userByEmail = userService.getUserByEmail(userEmail) ?: error("No user found with $userEmail")

                val (result, errorMsg) = logAndGetError(logger) {
                    bankTransactionImportService.doImport(
                        userByEmail.id,
                        request.ledgerId,
                        request.bankAccountId,
                        ByteArrayInputStream(Base64.getDecoder().decode(request.dataBase64)),
                        request.filename,
                        request.duplicationHandlerType.duplicationHandler
                    )
                }

                RpcResponse(importBankTransactionsResult = result, error = errorMsg)
            }

            addNewMatcher -> {
                val userByEmail = userService.getUserByEmail(userEmail) ?: error("No user found with $userEmail")

                val (_, errorMsg) = logAndGetError(logger) {
                    transactionMatchingService.addNewMatcher(
                        userByEmail.id,
                        rpcRequest.addNewMatcherRequest!!
                    )
                }

                RpcResponse(error = errorMsg)
            }

            getMatchCandidates -> {
                val userByEmail = userService.getUserByEmail(userEmail) ?: error("No user found with $userEmail")

                val getMatchCandidatesRequest = rpcRequest.getMatchCandidatesRequest!!

                val result = transactionMatchingService.getMatchCandidates(
                    userByEmail.id,
                    getMatchCandidatesRequest.ledgerId,
                    getMatchCandidatesRequest.bankAccountId,
                    getMatchCandidatesRequest.transactionId,
                )

                RpcResponse(getMatchCandidatesResult = result)
            }

            executeMatcher -> {
                val userByEmail = userService.getUserByEmail(userEmail) ?: error("No user found with $userEmail")
                val matcherRequest = rpcRequest.executeMatcherRequest!!

                val (_, error) = logAndGetError(logger) {
                    transactionMatchingService.executeMatch(
                        userByEmail.id,
                        matcherRequest.ledgerId,
                        matcherRequest.bankAccountId,
                        matcherRequest.transactionId,
                        matcherRequest.matcherId,
                    )
                }

                RpcResponse(error = error)
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