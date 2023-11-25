package com.dehnes.accounting.database

import com.dehnes.accounting.api.ChangeEvent
import com.dehnes.accounting.api.ChangeLogEventTypeV2
import java.sql.Connection
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.sql.DataSource


class Listener(
    val id: String,
    val filter: (event: ChangeEvent) -> Boolean,
    val onEvent: (event: ChangeEvent) -> Unit
)


class Changelog(private val dataSource: DataSource) {

    val writeTransactionListeners = ConcurrentHashMap<String, Listener>()

    private val threadLocalChangeLog = ThreadLocal<Queue<ChangeEvent>>()

    fun <T> writeTx(fn: (conn: Connection) -> T): T {
        threadLocalChangeLog.set(LinkedList())
        val result: T?

        try {
            dataSource.connection.use { conn ->
                val cnt = Transactions.dbConnectionCounter.incrementAndGet()
                Transactions.logger.debug { "Opened connection. new cnt=$cnt" }

                conn.autoCommit = false
                try {
                    result = fn(conn)
                    conn.commit()
                } finally {
                    val cnt2 = Transactions.dbConnectionCounter.decrementAndGet()
                    Transactions.logger.debug { "Closed connection. new cnt=$cnt2" }
                    conn.rollback()
                }
            }

            handleChanges(threadLocalChangeLog.get())
        } finally {
            threadLocalChangeLog.remove()
        }

        return result!!
    }

    fun handleChanges(events: Collection<ChangeEvent>) {
        val compressed = events.toSet()

        compressed.forEach { event ->
            writeTransactionListeners.filter { it.value.filter(event) }.forEach { (_, l) ->
                l.onEvent(event)
            }
        }
    }


    fun add(type: ChangeLogEventTypeV2) {
        threadLocalChangeLog.get()?.add(ChangeEvent(type))
    }
}
