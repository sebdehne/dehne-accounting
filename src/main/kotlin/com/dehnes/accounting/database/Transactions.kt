package com.dehnes.accounting.database

import io.github.oshai.kotlinlogging.KotlinLogging
import java.sql.Connection
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.sql.DataSource

object Transactions {

    private val lock = ReentrantReadWriteLock()
    private val readLock = lock.readLock()
    private val writeLock = lock.writeLock()

    var dbConnectionCounter = AtomicLong(0)

    val logger = KotlinLogging.logger { }

    fun <T> countDb(fn: () -> T) = try {
        val cnt = dbConnectionCounter.incrementAndGet()
        logger.debug { "Opened connection. new cnt=$cnt stack=${stackTraceShort()}" }
        fn()
    } finally {
        val cnt2 = dbConnectionCounter.decrementAndGet()
        logger.debug { "Closed connection. new cnt=$cnt2 stack=${stackTraceShort()}" }
    }

    fun <T> DataSource.readTx(fn: (conn: Connection) -> T): T = countDb {
        check(readLock.tryLock(10, TimeUnit.SECONDS)) {"Could not get DB read lock"}
        try {
            this.connection.use { connection ->
                connection.autoCommit = false

                try {
                    fn(connection)
                } finally {
                    connection.rollback()
                }
            }
        } finally {
            readLock.unlock()
        }
    }

    fun <T> DataSource.writeTx(fn: (conn: Connection) -> T): T = countDb {
        check(writeLock.tryLock(10, TimeUnit.SECONDS)) {"Could not get DB write lock"}
        try {
            this.connection.use { connection ->
                connection.autoCommit = false

                try {
                    val r = fn(connection)
                    connection.commit()
                    r
                } finally {
                    connection.rollback()
                }
            }
        } finally {
            writeLock.unlock()
        }
    }

    private fun stackTraceShort() = Thread.currentThread().stackTrace.mapNotNull {
        if (it.className.startsWith("com.dehnes")) {
            it.className.split(".").last() + "." + it.methodName + ":" + it.lineNumber
        } else null
    }.joinToString(", ")

}