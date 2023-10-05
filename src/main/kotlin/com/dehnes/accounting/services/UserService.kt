package com.dehnes.accounting.services

import com.dehnes.accounting.database.Transactions.readTx
import com.dehnes.accounting.domain.InformationElement
import java.sql.Connection
import javax.sql.DataSource

class UserService(
    private val dataSource: DataSource
) {

    fun getUserByEmail(userEmail: String) = dataSource.readTx { getUserByEmail(it, userEmail) }

    fun getUserById(connection: Connection, userId: String) =
        connection.prepareStatement("SELECT * FROM user where id = ?").use { preparedStatement ->
            preparedStatement.setString(1, userId)
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