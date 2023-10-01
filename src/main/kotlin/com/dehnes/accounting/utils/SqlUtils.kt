package com.dehnes.accounting.utils

import java.sql.PreparedStatement
import java.sql.Timestamp
import java.time.Instant

object SqlUtils {

    fun setSqlParams(preparedStatement: PreparedStatement, params: List<Any>) {

        params.forEachIndexed { index, any ->

            when {
                any is Long -> preparedStatement.setLong(index + 1, any)
                any is String -> preparedStatement.setString(index + 1, any)
                any is Instant -> preparedStatement.setTimestamp(index + 1, Timestamp.from(any))
                else -> error("Unsupported type $any")
            }
        }
    }

}