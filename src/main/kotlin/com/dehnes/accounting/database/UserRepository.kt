package com.dehnes.accounting.database

import com.dehnes.accounting.api.dtos.UserStateV2
import com.dehnes.accounting.database.Transactions.writeTx
import com.dehnes.accounting.domain.InformationElement
import com.dehnes.accounting.services.AccessLevel
import com.dehnes.accounting.utils.toInt
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.sql.Connection
import javax.sql.DataSource

class UserRepository(
    private val dataSource: DataSource,
    private val objectMapper: ObjectMapper,
) {

    fun getUserRealms(connection: Connection): List<UserRealm> =
        connection.prepareStatement("SELECT * FROM user_realm").use { preparedStatement ->
            preparedStatement.executeQuery().use { rs ->
                val l = mutableListOf<UserRealm>()
                while (rs.next()) {
                    l.add(
                        UserRealm(
                            rs.getString("user_id"),
                            rs.getString("ledger_id"),
                            AccessLevel.valueOf(rs.getString("access_level")),
                        )
                    )
                }
                l
            }
        }

    fun setUserState(conn: Connection, userId: String, userStateV2: UserStateV2) {
        val updated = conn.prepareStatement("UPDATE user_state SET frontend_state = ? WHERE user_id = ?")
            .use { preparedStatement ->
                preparedStatement.setString(1, objectMapper.writeValueAsString(userStateV2))
                preparedStatement.setString(2, userId)
                preparedStatement.executeUpdate() > 0
            }
        if (!updated) {
            conn.prepareStatement("INSERT INTO user_state (user_id, frontend_state) VALUES (?,?)")
                .use { preparedStatement ->
                    preparedStatement.setString(1, userId)
                    preparedStatement.setString(2, objectMapper.writeValueAsString(userStateV2))
                    preparedStatement.executeUpdate()
                }
        }
    }

    fun setUserState(userId: String, userStateV2: UserStateV2) {
        dataSource.writeTx { conn -> setUserState(conn, userId, userStateV2) }
    }

    fun getUserState(conn: Connection, userId: String) =
        conn.prepareStatement("SELECT * FROM user_state WHERE user_id = ?").use { preparedStatement ->
            preparedStatement.setString(1, userId)
            preparedStatement.executeQuery().use { rs ->
                if (rs.next()) {
                    objectMapper.readValue<UserStateV2>(rs.getString("frontend_state"))
                } else null
            }
        }

    fun getUser(conn: Connection, id: String) =
        conn.prepareStatement("SELECT * FROM user WHERE id = ?").use { preparedStatement ->
            preparedStatement.setString(1, id)
            preparedStatement.executeQuery().use { rs ->
                check(rs.next())
                User(
                    rs.getString("id"),
                    rs.getString("name"),
                    rs.getString("description"),
                    rs.getString("user_email"),
                    rs.getLong("active") > 0,
                    rs.getLong("is_admin") > 0,
                )
            }
        }

    fun insert(user: User) {
        dataSource.writeTx { conn ->
            conn.prepareStatement(
                """
                INSERT INTO user (
                    id, 
                    name, 
                    description, 
                    user_email, 
                    active, 
                    is_admin
                ) VALUES (?,?,?,?,?,?)
            """.trimIndent()
            )
                .use { preparedStatement ->
                    preparedStatement.setString(1, user.id)
                    preparedStatement.setString(2, user.name)
                    preparedStatement.setString(3, user.description)
                    preparedStatement.setString(4, user.userEmail)
                    preparedStatement.setInt(5, user.active.toInt())
                    preparedStatement.setInt(6, user.active.toInt())
                    preparedStatement.executeUpdate()
                }
        }
    }
}

data class User(
    override val id: String,
    override val name: String,
    override val description: String?,
    val userEmail: String,
    val active: Boolean,
    val admin: Boolean,
) : InformationElement()

