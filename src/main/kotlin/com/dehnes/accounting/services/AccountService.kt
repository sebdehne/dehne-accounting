package com.dehnes.accounting.services

import com.dehnes.accounting.database.*
import com.dehnes.accounting.database.Transactions.writeTx
import com.dehnes.accounting.domain.StandardAccount
import java.util.*
import javax.sql.DataSource

class AccountService(
    private val dataSource: DataSource,
    private val authorizationService: AuthorizationService,
    private val accountsRepository: AccountsRepository,
    private val bookingRepository: BookingRepository,
    private val unbookedBankTransactionMatcherRepository: UnbookedBankTransactionMatcherRepository,
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

            unbookedBankTransactionMatcherRepository.removeForAccountId(
                conn,
                realmId,
                sourceAccountId,
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

    fun createOrUpdateAccount(userId: String, realmId: String, account: AccountDto) {
        dataSource.writeTx { conn ->
            authorizationService.assertAuthorization(
                conn,
                userId,
                realmId,
                AccessRequest.owner
            )

            check(StandardAccount.entries.none { a -> a.toAccountId(realmId) == account.id })
            check(account.parentAccountId != null)

            val existing = accountsRepository.getAll(conn, realmId).firstOrNull { it.id == account.id }

            if (existing == null) {
                accountsRepository.insert(
                    conn,
                    account.copy(
                        id = UUID.randomUUID().toString(),
                        realmId = realmId,
                    )
                )
            } else {
                accountsRepository.updateAccount(
                    conn,
                    account
                )
            }

        }
    }

}