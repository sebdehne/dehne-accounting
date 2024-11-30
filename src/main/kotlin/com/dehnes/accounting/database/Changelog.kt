package com.dehnes.accounting.database

import com.dehnes.accounting.api.ChangeEvent
import com.dehnes.accounting.api.ChangeLogEventTypeV2
import com.dehnes.accounting.utils.wrap
import io.github.oshai.kotlinlogging.KotlinLogging
import java.sql.Connection
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
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

    /*
     * We get error sometimes:
     *  [SQLITE_BUSY] The database file is locked (database is locked)
     *
     * https://www.reddit.com/r/sqlite/comments/x76sxj/need_help_with_sqlite_busy_database_is_locked/
     */
    private val writeSemaphore = Semaphore(1)

    fun <T> writeTx(fn: (conn: Connection) -> T): T = Transactions.countDb {
        threadLocalChangeLog.set(LinkedList())
        val result: T
        try {
            check(writeSemaphore.tryAcquire(10, TimeUnit.SECONDS)) { "Timeout getting write access" }
            try {
                dataSource.connection.use { conn ->
                    conn.autoCommit = false
                    try {
                        result = fn(conn)
                        conn.commit()
                        handleChanges(threadLocalChangeLog.get())
                        result
                    } finally {
                        conn.rollback()
                    }
                }
            } finally {
                writeSemaphore.release()
            }
        } finally {
            threadLocalChangeLog.remove()
        }
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
