package com.dehnes.accounting.services

import com.dehnes.accounting.database.Transactions.readTx
import com.dehnes.accounting.domain.InformationElement
import com.dehnes.accounting.utils.toInt
import java.sql.Connection
import java.util.UUID
import javax.sql.DataSource

class UserService(
    private val dataSource: DataSource
) {

    fun getUserByEmail(userEmail: String) = dataSource.readTx { getUserByEmail(it, userEmail) }

    fun getOrCreateUserByEmail(conn: Connection, userEmail: String): User {
        return getUserByEmail(conn, userEmail) ?: run {
            val user = User(
                UUID.randomUUID().toString(),
                "Unknown",
                null,
                userEmail,
                true,
                false
            )
            conn.prepareStatement("INSERT INTO user (id, name, description, user_email, active, is_admin) VALUES (?,?,?,?,?,?)").use { preparedStatement ->
                preparedStatement.setString(1, user.id)
                preparedStatement.setString(2, user.name)
                preparedStatement.setString(3, user.description)
                preparedStatement.setString(4, user.userEmail)
                preparedStatement.setInt(5, user.isActive.toInt())
                preparedStatement.setInt(6, user.isAdmin.toInt())
                preparedStatement.executeUpdate()
            }
            user
        }
    }


    fun getUserByEmail(connection: Connection, userEmail: String) =
        connection.prepareStatement("SELECT * FROM user where user_email = ?").use { preparedStatement ->
            preparedStatement.setString(1, userEmail)
            preparedStatement.executeQuery().use { rs ->
                if (rs.next()) User(
                    rs.getString("id"),
                    rs.getString("name"),
                    rs.getString("description"),
                    rs.getString("user_email"),
                    rs.getInt("active") > 0,
                    rs.getInt("is_admin") > 0,
                ) else null
            }
        }


}

class User(
    override val id: String,
    override val name: String,
    override val description: String?,
    val userEmail: String,
    val isActive: Boolean,
    val isAdmin: Boolean,
) : InformationElement()