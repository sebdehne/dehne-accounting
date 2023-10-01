package com.dehnes.accounting.database

import java.sql.Connection

object KeyValueRepo {

    fun insert(connection: Connection, keyValueEntry: KeyValueEntry) {
        connection.prepareStatement("INSERT INTO key_value (entry_key, entry_value, entry_format, entry_version) VALUES (?,?,?,?)")
            .use { preparedStatement ->
                preparedStatement.setString(1, keyValueEntry.key)
                preparedStatement.setString(2, keyValueEntry.value)
                preparedStatement.setString(3, keyValueEntry.format)
                preparedStatement.setLong(4, keyValueEntry.version)
                check(preparedStatement.executeUpdate() == 1) { "Could not insert $keyValueEntry" }
            }
    }

    fun update(connection: Connection, keyValueEntry: KeyValueEntry) {
        connection.prepareStatement("UPDATE key_value SET entry_value=?, entry_version=?, entry_format=? WHERE entry_key = ?")
            .use { preparedStatement ->
                preparedStatement.setString(1, keyValueEntry.value)
                preparedStatement.setLong(2, keyValueEntry.version)
                preparedStatement.setString(3, keyValueEntry.format)
                preparedStatement.setString(4, keyValueEntry.key)
                check(preparedStatement.executeUpdate() == 1) { "Could not update $keyValueEntry" }
            }
    }

    fun remove(connection: Connection, key: String) {
        connection.prepareStatement("DELETE FROM key_value WHERE entry_key = ?").use { preparedStatement ->
            preparedStatement.setString(1, key)
            preparedStatement.executeUpdate()
        }
    }

    fun get(connection: Connection, key: String): KeyValueEntry? =
        connection.prepareStatement("SELECT * FROM key_value WHERE entry_key = ?").use { preparedStatement ->
            preparedStatement.setString(1, key)
            preparedStatement.executeQuery().use { rs ->
                if (!rs.next()) null else {
                    KeyValueEntry(
                        key = rs.getString("entryKey"),
                        value = rs.getString("entryValue"),
                        format = rs.getString("entryFormat"),
                        version = rs.getLong("entryVersion"),
                    )
                }
            }
        }

}

data class KeyValueEntry(
    val key: String,
    val value: String,
    val format: String,
    val version: Long = 0,
)

