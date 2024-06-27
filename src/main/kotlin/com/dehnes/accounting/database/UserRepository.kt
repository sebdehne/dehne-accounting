package com.dehnes.accounting.database

import com.dehnes.accounting.api.UserUpdated
import com.dehnes.accounting.api.dtos.RealmAccessLevel
import com.dehnes.accounting.api.dtos.User
import com.dehnes.accounting.api.dtos.UserStateV2
import com.dehnes.accounting.utils.toInt
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.sql.Connection

class UserRepository(
    private val changelog: Changelog,
    private val realmRepository: RealmRepository,
) {


    fun getUser(conn: Connection, id: String): User {
        val user = conn.prepareStatement("SELECT * FROM user WHERE id = ?").use { preparedStatement ->
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
                    emptyMap()
                )
            }
        }



        return if (user.admin) {
            val realmList = realmRepository.getAll(conn)
            user.copy(realmIdToAccessLevel = realmList.associate {
                it.id to RealmAccessLevel.owner
            })
        } else {
            val userRealms =
                conn.prepareStatement("SELECT * FROM user_realm where user_id = ?").use { preparedStatement ->
                    preparedStatement.setString(1, user.id)

                    preparedStatement.executeQuery().use { rs ->
                        val l = mutableListOf<Pair<String, RealmAccessLevel>>()
                        while (rs.next()) {
                            l.add(
                                rs.getString("realm_id") to RealmAccessLevel.valueOf(rs.getString("access_level"))
                            )
                        }
                        l
                    }
                }
            user.copy(realmIdToAccessLevel = userRealms.associate {
                it.first to it.second
            })
        }
    }

    fun addOrReplace(conn: Connection, user: User) {
        delete(conn, user.id)
        insert(conn, user)
    }

    fun delete(conn: Connection, userId: String) {
        conn.prepareStatement("DELETE FROM user_realm where user_id = ?").use {
            it.setString(1, userId)
            it.executeUpdate()
        }
        conn.prepareStatement("DELETE FROM user where id = ?").use {
            it.setString(1, userId)
            it.executeUpdate()
        }
    }

    private fun insert(conn: Connection, user: User) {
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
                preparedStatement.setInt(6, user.admin.toInt())
                preparedStatement.executeUpdate()
            }

        conn.prepareStatement("INSERT INTO user_realm (user_id, realm_id, access_level) VALUES (?,?,?)")
            .use { preparedStatement ->
                user.realmIdToAccessLevel.entries.forEach { e ->
                    preparedStatement.setString(1, user.id)
                    preparedStatement.setString(2, e.key)
                    preparedStatement.setString(3, e.value.name)
                    preparedStatement.addBatch()
                }
                preparedStatement.executeBatch()
            }

        changelog.add(UserUpdated)
    }

    fun getAllUsers(conn: Connection) = conn.prepareStatement("SELECT id FROM user").use { preparedStatement ->
        preparedStatement.executeQuery().use { rs ->
            val l = mutableListOf<String>()
            while (rs.next()) {
                l.add(rs.getString("id"))
            }
            l.map {
                getUser(conn, it)
            }
        }
    }
}


