package com.dehnes.accounting.services

import com.dehnes.accounting.api.DatabaseRestored
import com.dehnes.accounting.api.dtos.UserStateV2
import com.dehnes.accounting.database.Changelog
import com.dehnes.accounting.database.Transactions.readTx
import com.dehnes.accounting.database.UserStateRepository
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.sql.DataSource
import kotlin.concurrent.read
import kotlin.concurrent.write

class UserStateService(
    private val dataSource: DataSource,
    private val userStateRepository: UserStateRepository,
    private val changelog: Changelog,
) {

    private val userStateCacheLock = ReentrantReadWriteLock()
    private val userStateCache = mutableMapOf<String, UserStateV2>()

    init {
        UUID.randomUUID().toString().apply {
            changelog.syncListeners[this] = { e ->
                if (e.changeLogEventTypeV2 is DatabaseRestored) {
                    userStateCacheLock.write {
                        userStateCache.clear()
                    }
                }
            }
        }
    }

    fun getUserStateV2(userId: String): UserStateV2 = userStateCacheLock.read {
        userStateCache[userId]
    } ?: run {
        userStateCacheLock.write {
            userStateCache.getOrPut(userId) {
                dataSource.readTx { conn ->
                    userStateRepository.getUserState(conn, userId)
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

}