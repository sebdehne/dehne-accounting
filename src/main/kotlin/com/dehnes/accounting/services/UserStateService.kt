package com.dehnes.accounting.services

import com.dehnes.accounting.api.dtos.UserStateV2
import com.dehnes.accounting.database.Changelog
import com.dehnes.accounting.database.Transactions.readTx
import com.dehnes.accounting.database.UserStateRepository
import java.sql.Connection
import javax.sql.DataSource

class UserStateService(
    private val dataSource: DataSource,
    private val userStateRepository: UserStateRepository,
    private val userService: UserService,
    private val changelog: Changelog,
) {


    fun getUserStateV2(sessionId: String) =
        dataSource.readTx { conn -> userStateRepository.getUserStateViaSessionId(conn, sessionId) }

    fun getUserStateV2(conn: Connection, sessionId: String): UserStateV2 =
        userStateRepository.getUserStateViaSessionId(conn, sessionId)


    fun setUserStateV2(userId: String, userStateV2: UserStateV2) {
        changelog.writeTx { conn ->
            userStateRepository.setUserState(conn, userId, userStateV2)
        }
    }

    fun getLatestSessionIdOrCreateNew(userEmail: String, existingCookie: String?) = changelog.writeTx { conn ->
        val user = userService.getOrCreateUserByEmail(conn, userEmail)

        userStateRepository.getOrCreateUserStateId(
            conn,
            user.id,
            existingCookie
        )
    }

}