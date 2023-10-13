package com.dehnes.accounting

import com.dehnes.accounting.api.WebSocketServer
import com.dehnes.accounting.services.UserStateService
import com.dehnes.accounting.utils.StaticFilesServlet
import jakarta.websocket.HandshakeResponse
import jakarta.websocket.server.HandshakeRequest
import jakarta.websocket.server.ServerEndpointConfig
import mu.KotlinLogging
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.websocket.jakarta.server.config.JakartaWebSocketServletContainerInitializer
import java.time.Duration
import java.util.UUID

val configuration = Configuration()

fun main() {
    val logger = KotlinLogging.logger { }

    configuration.init()

    val server = Server()
    val connector = ServerConnector(server)
    connector.port = 9095
    server.addConnector(connector)

    val handler = ServletContextHandler(ServletContextHandler.SESSIONS)
    handler.contextPath = System.getProperty("CONTEXTPATH", "/").apply {
        logger.info { "Using contextPath=$this" }
    }
    server.handler = handler

    handler.addServlet(ServletHolder(StaticFilesServlet()), "/*")

    JakartaWebSocketServletContainerInitializer.configure(handler) { _, container ->
        container.defaultMaxSessionIdleTimeout = Duration.ofMinutes(10).toMillis()
        container.addEndpoint(
            ServerEndpointConfig.Builder
                .create(WebSocketServer::class.java, "/api")
                .configurator(object : ServerEndpointConfig.Configurator() {

                    val userStateService = configuration.getBean<UserStateService>()

                    override fun modifyHandshake(
                        sec: ServerEndpointConfig,
                        request: HandshakeRequest,
                        response: HandshakeResponse?
                    ) {
                        val userEmail = request.headers.entries.firstOrNull {
                            it.key.lowercase() == "x-email"
                        }?.value?.firstOrNull()
                        sec.userProperties!!["userEmail"] = userEmail

                        if (userEmail != null) {
                            val existingCookie = request.headers.entries
                                .filter { it.key.lowercase() == "cookie" }
                                .flatMap { it.value }
                                .firstOrNull { it.startsWith("dehne-accounting=") }
                                ?.split("=")
                                ?.get(1)
                                ?.trim()

                            val sessionId = userStateService.getLatestSessionIdOrCreateNew(
                                userEmail,
                                existingCookie
                            )

                            response!!.headers["Set-Cookie"] = listOf("dehne-accounting=$sessionId; Secure,HttpOnly")
                            sec.userProperties!!["sessionId"] = sessionId
                        }

                        super.modifyHandshake(sec, request, response)
                    }
                })
                .build()
        )
    }

    try {
        server.start()
        server.join()
    } catch (t: Throwable) {
        logger.error("", t)
    }
}

