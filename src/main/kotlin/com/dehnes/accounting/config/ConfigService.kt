package com.dehnes.accounting.config

import com.dehnes.accounting.database.KeyValueEntry
import com.dehnes.accounting.database.KeyValueRepo
import com.dehnes.accounting.database.Transactions.writeTx
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.sql.Connection
import javax.sql.DataSource

class ConfigService(
    private val objectMapper: ObjectMapper,
    private val keyValueRepo: KeyValueRepo,
    dataSource: DataSource,
) {

    private val key = "CONFIG"

    init {
        dataSource.writeTx { conn: Connection ->
            val existing = keyValueRepo.get(conn, key)
            if (existing == null) {
                keyValueRepo.insert(
                    conn, KeyValueEntry(
                        key,
                        objectMapper.writeValueAsString(ConfigurationRoot()),
                        "v1",
                        1L
                    )
                )
            }
        }
    }

    private fun readConfig(connection: Connection) = keyValueRepo.get(connection, key)!!.let {
        check(it.format == "v1")
        objectMapper.readValue<ConfigurationRoot>(it.value)
    }

    fun isDevMode(connection: Connection) = readConfig(connection).devMode


}