package com.dehnes.accounting.database

import mu.KotlinLogging
import java.sql.Connection
import java.util.concurrent.atomic.AtomicLong
import javax.sql.DataSource

object Transactions {

    var dbConnectionCounter = AtomicLong(0)

    val logger = KotlinLogging.logger {  }

    fun <T> DataSource.readTx(fn: (conn: Connection) -> T): T {
        var t: T? = null
        this.connection.use { connection ->
            val cnt = dbConnectionCounter.incrementAndGet()
            logger.debug { "Opened connection. new cnt=$cnt" }
            connection.autoCommit = false

            try {
                t = fn(connection)
            } finally {
                val cnt2 = dbConnectionCounter.decrementAndGet()
                logger.debug { "Closed connection. new cnt=$cnt2" }
                connection.rollback()
            }
        }

        return t!!
    }


}