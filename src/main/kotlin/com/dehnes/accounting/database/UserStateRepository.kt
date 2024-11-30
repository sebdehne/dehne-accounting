package com.dehnes.accounting.database

import com.dehnes.accounting.api.UserStateUpdated
import com.dehnes.accounting.api.dtos.UserStateV2
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant

class UserStateRepository(
    private val objectMapper: ObjectMapper,
    private val changelog: Changelog,
) {

    fun setUserState(conn: Connection, userId: String, userStateV2: UserStateV2) {
        val updated = conn.prepareStatement(
            """
                UPDATE user_state SET user_state = ?, last_modified = ? WHERE id = ?
            """.trimIndent()
        ).use { preparedStatement ->
            preparedStatement.setString(1, objectMapper.writeValueAsString(userStateV2))
            preparedStatement.setTimestamp(2, Timestamp.from(Instant.now()))
            preparedStatement.setString(3, userStateV2.id)
            preparedStatement.executeUpdate() > 0
        }
        if (!updated) {
            conn.prepareStatement(
                """
                    INSERT INTO user_state (id, user_id, user_state,last_modified) VALUES (?,?,?,?)
                """.trimIndent()
            ).use { preparedStatement ->
                preparedStatement.setString(1, userStateV2.id)
                preparedStatement.setString(2, userId)
                preparedStatement.setString(3, objectMapper.writeValueAsString(userStateV2))
                preparedStatement.setTimestamp(4, Timestamp.from(Instant.now()))
                preparedStatement.executeUpdate()
            }
        }

        changelog.add(UserStateUpdated)
    }

    fun getMostRecentUserState(conn: Connection, userId: String): UserStateV2? =
        conn.prepareStatement("SELECT * FROM user_state where user_id = ? order by last_modified desc limit 1")
            .use { preparedStatement ->
                preparedStatement.setString(1, userId)
                preparedStatement.executeQuery().use { rs ->
                    if (rs.next()) {
                        objectMapper.readValue(rs.getString("user_state"))
                    } else null
                }
            }

    fun getUserState(conn: Connection, userId: String): UserStateV2 {
        return conn.prepareStatement("SELECT * FROM user_state WHERE user_id = ?").use { preparedStatement ->
            preparedStatement.setString(1, userId)
            preparedStatement.executeQuery().use { rs ->
                check(rs.next())
                objectMapper.readValue(rs.getString("user_state"))
            }
        }
    }

}
