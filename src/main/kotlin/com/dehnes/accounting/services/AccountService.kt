package com.dehnes.accounting.services

import com.dehnes.accounting.database.AccountsRepository
import com.dehnes.accounting.database.BookingRepository
import com.dehnes.accounting.database.Transactions.writeTx
import javax.sql.DataSource

class AccountService(
    private val dataSource: DataSource,
    private val authorizationService: AuthorizationService,
    private val accountsRepository: AccountsRepository,
    private val bookingRepository: BookingRepository,
) {

    fun merge(
        userId: String,
        realmId: String,
        sourceAccountId: String,
        targetAccountId: String
    ) {

        dataSource.writeTx { conn ->
            authorizationService.assertAuthorization(
                conn,
                userId,
                realmId,
                AccessRequest.owner
            )

            bookingRepository.mergeAccount(
                conn,
                realmId,
                sourceAccountId,
                targetAccountId
            )


            // if the account has no children-accounts, remove it
            if (accountsRepository.getAll(conn, realmId).none { it.parentAccountId == sourceAccountId }) {
                accountsRepository.remove(conn, sourceAccountId)
            }

        }
    }

}