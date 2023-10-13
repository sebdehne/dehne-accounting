package com.dehnes.accounting.services

import com.dehnes.accounting.api.dtos.UserState
import com.dehnes.accounting.api.dtos.UserStateV2
import com.dehnes.accounting.database.Repository
import com.dehnes.accounting.database.Transactions.readTx
import com.dehnes.accounting.database.Transactions.writeTx
import com.dehnes.accounting.database.UserRepository
import com.dehnes.accounting.database.UserStateRepository
import java.sql.Connection
import javax.sql.DataSource

class UserStateService(
    private val dataSource: DataSource,
    private val repository: Repository,
    private val userStateRepository: UserStateRepository,
    private val userService: UserService,
) {

    fun getUserState(conn: Connection, userId: String): UserState {
        return repository.getUserState(conn, userId)
    }

    fun getUserStateV2(sessionId: String) = dataSource.readTx { conn -> userStateRepository.getUserStateViaSessionId(conn, sessionId) }

    fun getUserStateV2(conn: Connection, sessionId: String): UserStateV2 =
        userStateRepository.getUserStateViaSessionId(conn, sessionId)

    fun setUserState(userId: String, userState: UserState) {
        dataSource.writeTx { conn ->
            repository.setUserState(conn, userId, userState)
        }
    }

    fun setUserStateV2(userId: String, userStateV2: UserStateV2) {
        dataSource.writeTx { conn ->
            userStateRepository.setUserState(conn, userId, userStateV2)
        }
    }

    fun getLatestSessionIdOrCreateNew(userEmail: String, existingCookie: String?) = dataSource.writeTx { conn ->
        val user = userService.getOrCreateUserByEmail(conn, userEmail)

        userStateRepository.getOrCreateUserStateId(
            conn,
            user.id,
            existingCookie
        )
    }

}