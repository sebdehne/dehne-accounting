package com.dehnes.accounting.services

import com.dehnes.accounting.api.dtos.UpdateBudget
import com.dehnes.accounting.database.BudgetDbRecord
import com.dehnes.accounting.database.BudgetRepository
import com.dehnes.accounting.database.Changelog
import com.dehnes.accounting.database.Transactions.readTx
import javax.sql.DataSource

class BudgetService(
    private val budgetRepository: BudgetRepository,
    private val authorizationService: AuthorizationService,
    private val dataSource: DataSource,
    private val changelog: Changelog,
) {

    fun getBudgetRules(
        userId: String,
        realmId: String,
        accountId: String
    ) = dataSource.readTx { conn ->
        authorizationService.assertAuthorization(
            conn,
            userId,
            realmId,
            AccessRequest.read
        )

        budgetRepository.getAll(conn, realmId).filter { it.accountId == accountId }
    }

    fun addOrReplace(userId: String, realmId: String, budgetRule: BudgetDbRecord) {
        changelog.writeTx { conn ->
            authorizationService.assertAuthorization(
                conn,
                userId,
                realmId,
                AccessRequest.write
            )

            budgetRepository.insertOrUpdate(
                conn,
                budgetRule,
            )
        }
    }

    fun updateBudgetRulesForAccount(
        userId: String,
        realmId: String,
        updateBudget: UpdateBudget,
    ) {
        changelog.writeTx { conn ->
            authorizationService.assertAuthorization(
                conn,
                userId,
                realmId,
                AccessRequest.write
            )

            (1..12).map { m ->

                budgetRepository.delete(
                    connection = conn,
                    realmId = realmId,
                    accountId = updateBudget.accountId,
                    month = m
                )

                updateBudget.budget[m.toString()]?.let {
                    budgetRepository.insertOrUpdate(
                        conn,
                        BudgetDbRecord(
                            realmId = realmId,
                            accountId = updateBudget.accountId,
                            month = m,
                            amountInCents = it
                        )
                    )
                }
            }

        }
    }

    fun getBudgetAccounts(userId: String, realmId: String): List<String> = dataSource.readTx { conn ->
        authorizationService.assertAuthorization(
            conn,
            userId,
            realmId,
            AccessRequest.read
        )

        budgetRepository.getAll(conn, realmId)
            .map { it.accountId }
            .distinct()
    }

}