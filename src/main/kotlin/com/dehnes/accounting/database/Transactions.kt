package com.dehnes.accounting.database

import io.github.oshai.kotlinlogging.KotlinLogging
import java.sql.Connection
import java.util.concurrent.atomic.AtomicLong
import javax.sql.DataSource

object Transactions {

    var dbConnectionCounter = AtomicLong(0)

    val logger = KotlinLogging.logger { }

    fun <T> countDb(fn: () -> T) = try {
        val cnt = dbConnectionCounter.incrementAndGet()
        logger.debug { "Opened connection. new cnt=$cnt" }
        fn()
    } finally {
        val cnt2 = dbConnectionCounter.decrementAndGet()
        logger.debug { "Closed connection. new cnt=$cnt2" }
    }

    fun <T> DataSource.readTx(fn: (conn: Connection) -> T): T = countDb {
        this.connection.use { connection ->
            connection.autoCommit = false

            try {
                fn(connection)
            } finally {
                connection.rollback()
            }
        }
    }

}