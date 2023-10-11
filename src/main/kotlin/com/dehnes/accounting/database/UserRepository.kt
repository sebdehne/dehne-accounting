package com.dehnes.accounting.database

import com.dehnes.accounting.database.Transactions.writeTx
import com.dehnes.accounting.domain.InformationElement
import com.dehnes.accounting.utils.toInt
import javax.sql.DataSource

class UserRepository(
    private val dataSource: DataSource
) {

    fun insert(user: User) {
        dataSource.writeTx { conn ->
            conn.prepareStatement("INSERT INTO user (id, name, description, user_email, active, is_admin) VALUES (?,?,?,?,?,?)")
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