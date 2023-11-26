package com.dehnes.accounting.services

import com.dehnes.accounting.api.dtos.User
import com.dehnes.accounting.api.dtos.UserInfo
import com.dehnes.accounting.database.Changelog
import com.dehnes.accounting.database.RealmRepository
import com.dehnes.accounting.database.Transactions.readTx
import com.dehnes.accounting.database.UserRepository
import java.sql.Connection
import java.util.*
import javax.sql.DataSource

class UserService(
    private val dataSource: DataSource,
    private val userRepository: UserRepository,
    private val authorizationService: AuthorizationService,
    private val changelog: Changelog,
) {

    fun getUserInfo(userId: String) = dataSource.readTx { conn ->

        val realmInfo = authorizationService.getRealmInfo(conn, userId)
        val user = userRepository.getUser(conn, userId)

        UserInfo(
            user.admin,
            realmInfo
        )
    }

    fun getAllUsers(userId: String): List<User> = dataSource.readTx {
        authorizationService.assertIsAdmin(it, userId)

        userRepository.getAllUsers(it)
    }


    fun getUserByEmail(userEmail: String) = dataSource.readTx { getUserByEmail(it, userEmail) }

    fun getOrCreateUserByEmail(conn: Connection, userEmail: String): User {
        return getUserByEmail(conn, userEmail) ?: run {
            val user = User(
                UUID.randomUUID().toString(),
                "Unknown",
                null,
                userEmail,
                true,
                false,
                emptyMap()
            )

            userRepository.addOrReplace(conn, user)

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
                    emptyMap()
                ) else null
            }
        }

    fun addOrReplaceUser(userId: String, user: User) {
        changelog.writeTx { conn ->
            authorizationService.assertIsAdmin(conn, userId)
            userRepository.addOrReplace(conn, user)
        }
    }

    fun deleteUser(userId: String, deleteUserId: String) {
        changelog.writeTx { conn ->
            authorizationService.assertIsAdmin(conn, userId)
            userRepository.delete(conn, deleteUserId)
        }
    }

}

