package com.dehnes.accounting.services

import com.dehnes.accounting.api.dtos.UserState
import com.dehnes.accounting.database.Repository
import com.dehnes.accounting.database.Transactions.readTx
import com.dehnes.accounting.database.Transactions.writeTx
import javax.sql.DataSource

class UserStateService(
    private val dataSource: DataSource,
    private val repository: Repository,
) {

    fun getUserState(userId: String) = dataSource.readTx { conn ->
        repository.getUserState(conn, userId)
    }

    fun setUserState(userId: String, userState: UserState) {
        dataSource.writeTx { conn ->
            repository.setUserState(conn, userId, userState)
        }
    }

}