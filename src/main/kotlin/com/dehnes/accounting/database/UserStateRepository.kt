package com.dehnes.accounting.database

import com.dehnes.accounting.api.UserStateUpdated
import com.dehnes.accounting.api.dtos.UserStateV2
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.*

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

        changelog.add(UserStateUpdated(getSessionsFor(conn, userStateV2.id)))
    }

    fun getSessionsFor(conn: Connection, userStateId: String) =
        conn.prepareStatement("SELECT id FROM user_state_session WHERE user_state_id = ?").use { preparedStatement ->
            preparedStatement.setString(1, userStateId)
            preparedStatement.executeQuery().use { rs ->
                val l = mutableListOf<String>()
                while (rs.next()) {
                    l.add(rs.getString("id"))
                }
                l
            }
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

    fun getUserStateViaSessionId(conn: Connection, sessionId: String): UserStateV2 {
        val userStateId =
            conn.prepareStatement("SELECT * FROM user_state_session WHERE id = ?").use { preparedStatement ->
                preparedStatement.setString(1, sessionId)
                preparedStatement.executeQuery().use { rs ->
                    check(rs.next())
                    rs.getString("user_state_id")
                }
            }
        return conn.prepareStatement("SELECT * FROM user_state WHERE id = ?").use { preparedStatement ->
            preparedStatement.setString(1, userStateId)
            preparedStatement.executeQuery().use { rs ->
                check(rs.next())
                objectMapper.readValue(rs.getString("user_state"))
            }
        }
    }

    fun getOrCreateUserStateId(conn: Connection, userId: String, id: String?): String {
        val exists = id?.let {
            conn.prepareStatement("SELECT user_state_id FROM user_state_session WHERE id = ?").use {
                it.setString(1, id)
                it.executeQuery().use { rs ->
                    rs.next()
                }
            }
        } ?: false

        return if (exists) {
            conn.prepareStatement("UPDATE user_state_session set last_used = ? WHERE id = ?").use {
                it.setTimestamp(1, Timestamp.from(Instant.now()))
                it.setString(2, id)
                it.executeUpdate()
            }
            id!!
        } else {
            var mostRecentUserState = getMostRecentUserState(conn, userId)
            if (mostRecentUserState == null) {
                val newUserStateV2 = UserStateV2(UUID.randomUUID().toString())
                setUserState(conn, userId, newUserStateV2)
                mostRecentUserState = newUserStateV2
            }

            val newSessionId = id ?: UUID.randomUUID().toString()

            conn.prepareStatement("INSERT INTO user_state_session (id, user_state_id, last_used) VALUES (?,?,?)")
                .use {
                    it.setString(1, newSessionId)
                    it.setString(2, mostRecentUserState.id)
                    it.setTimestamp(3, Timestamp.from(Instant.now()))
                    it.executeUpdate()
                }

            newSessionId
        }
    }

}
