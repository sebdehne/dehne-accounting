package com.dehnes.accounting.services

import com.dehnes.accounting.api.dtos.UserStateV2
import com.dehnes.accounting.database.Changelog
import com.dehnes.accounting.database.Transactions.readTx
import com.dehnes.accounting.database.UserStateRepository
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.sql.DataSource
import kotlin.concurrent.read
import kotlin.concurrent.write

class UserStateService(
    private val dataSource: DataSource,
    private val userStateRepository: UserStateRepository,
    private val userService: UserService,
    private val changelog: Changelog,
) {

    private val userStateCacheLock = ReentrantReadWriteLock()
    private val userStateCache = mutableMapOf<String, UserStateV2>()

    fun getUserStateV2(sessionId: String): UserStateV2 = userStateCacheLock.read {
        userStateCache[sessionId]
    } ?: run {
        userStateCacheLock.write {
            userStateCache.getOrPut(sessionId) {
                dataSource.readTx { conn ->
                    userStateRepository.getUserStateViaSessionId(conn, sessionId)
                }
            }
        }
    }

    fun setUserStateV2(userId: String, userStateV2: UserStateV2) {
        changelog.writeTx { conn ->
            userStateCacheLock.write {
                userStateRepository.setUserState(conn, userId, userStateV2)
                userStateCache.clear()
            }
        }
    }

    fun getLatestSessionIdOrCreateNew(userEmail: String, existingCookie: String?) = changelog.writeTx { conn ->
        val user = userService.getOrCreateUserByEmail(conn, userEmail)
        check(user.active) { "User $userEmail not active" }
        userStateRepository.getOrCreateUserStateId(
            conn,
            user.id,
            existingCookie
        )
    }

}