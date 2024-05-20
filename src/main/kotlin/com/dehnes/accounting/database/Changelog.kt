package com.dehnes.accounting.database

import com.dehnes.accounting.api.ChangeEvent
import com.dehnes.accounting.api.ChangeLogEventTypeV2
import com.dehnes.accounting.utils.wrap
import io.github.oshai.kotlinlogging.KotlinLogging
import java.sql.Connection
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import javax.sql.DataSource


class Listener(
    val id: String,
    val filter: (event: ChangeEvent) -> Boolean,
    val onEvent: (event: ChangeEvent) -> Unit
)

class Changelog(
    private val dataSource: DataSource,
    private val executorService: ExecutorService,
) {
    private val logger = KotlinLogging.logger { }

    val syncListeners = ConcurrentHashMap<String, (e: ChangeEvent) -> Unit>()
    val asyncListeners = ConcurrentHashMap<String, Listener>()

    private val threadLocalChangeLog = ThreadLocal<Queue<ChangeEvent>>()

    fun <T> writeTx(fn: (conn: Connection) -> T): T {
        threadLocalChangeLog.set(LinkedList())
        val result: T?

        try {
            dataSource.connection.use { conn ->
                conn.autoCommit = false
                try {
                    result = fn(conn)
                    conn.commit()
                    handleChanges(threadLocalChangeLog.get())
                } finally {
                    conn.rollback()
                }
            }
        } finally {
            threadLocalChangeLog.remove()
        }

        return result!!
    }

    fun handleChanges(events: Collection<ChangeEvent>) {
        val compressed = events.toSet()

        // sync listeners
        compressed.forEach { change ->
            syncListeners.forEach { (_, l) ->
                l.invoke(change)
            }
        }

        executorService.submit(wrap(logger) {
            compressed.forEach { event ->
                asyncListeners
                    .filter { it.value.filter(event) }
                    .forEach { (_, l) ->
                        l.onEvent(event)
                    }
            }
        })
    }

    fun add(type: ChangeLogEventTypeV2) {
        threadLocalChangeLog.get()?.add(ChangeEvent(type))
    }
}
