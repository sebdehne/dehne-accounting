package com.dehnes.accounting.database

import java.sql.Connection
import javax.sql.DataSource

object Transactions {

    fun <T> DataSource.readTx(fn: (conn: Connection) -> T): T {
        var t: T? = null
        this.connection.use { connection ->
            connection.autoCommit = false

            try {
                t = fn(connection)
            } finally {
                connection.rollback()
            }
        }

        return t!!
    }


}