package com.dehnes.accounting.services

import com.dehnes.accounting.api.RealmChanged
import com.dehnes.accounting.database.*
import com.dehnes.accounting.database.RealmRepository.CloseDirection
import com.dehnes.accounting.utils.DateTimeUtils
import java.time.LocalDate
import java.util.*
import javax.sql.DataSource

class RealmService(
    private val realmRepository: RealmRepository,
    private val dataSource: DataSource,
    private val authorizationService: AuthorizationService,
    private val changelog: Changelog,
    private val unbookedTransactionRepository: UnbookedTransactionRepository,
    private val budgetRepository: BudgetRepository,
    private val budgetHistoryRepository: BudgetHistoryRepository,
) {

    @Volatile
    private var cache: List<Realm>? = null

    init {
        changelog.syncListeners[UUID.randomUUID().toString()] = {
            if (it.changeLogEventTypeV2 == RealmChanged) {
                cache = null
            }
        }
    }

    fun getAll(): List<Realm> {
        var c = cache
        if (c != null) return c

        c = dataSource.connection.use {
            it.autoCommit = false
            realmRepository.getAll(it)
        }

        cache = c

        return c
    }

    fun updateClosure(userId: String, realmId: String, direction: CloseDirection) {
        changelog.writeTx { conn ->
            authorizationService.assertAuthorization(conn, userId, realmId, AccessRequest.owner)

            val realm = realmRepository.getAll(
                conn
            ).firstOrNull { it.id == realmId } ?: error("Could not find realmId=$realmId")

            if (direction == CloseDirection.forward) {
                val currentClosure = LocalDate.of(
                    realm.closedYear,
                    realm.closedMonth,
                    1,
                )
                val nextClosure = currentClosure.plusMonths(1)

                check(
                    !unbookedTransactionRepository.hasUnbookedTransaction(
                        conn,
                        realmId,
                        nextClosure.atStartOfDay(DateTimeUtils.zoneId).toInstant(),
                        nextClosure.plusMonths(1).atStartOfDay(DateTimeUtils.zoneId).toInstant(),
                    )
                ) { "There are unbooked transactions in this periode - cannot close" }

                // copy current budget -> budget-history
                budgetRepository.getAll(conn, realmId).filter { it.month == nextClosure.month.value }.forEach { budget ->
                    budgetHistoryRepository.insertOrUpdate(BudgetHistory(
                        realmId,
                        nextClosure.year,
                        nextClosure.month.value,
                        budget.accountId,
                        budget.amountInCents
                    ))
                }
            }

            realmRepository.updateClosure(conn, realmId, direction)
        }
    }


}