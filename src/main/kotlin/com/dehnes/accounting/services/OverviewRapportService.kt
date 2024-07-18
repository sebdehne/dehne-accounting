package com.dehnes.accounting.services

import com.dehnes.accounting.database.*
import com.dehnes.accounting.database.Transactions.readTx
import com.dehnes.accounting.domain.StandardAccount
import com.dehnes.accounting.utils.DateTimeUtils
import java.time.LocalDate
import javax.sql.DataSource


class OverviewRapportService(
    private val dataSource: DataSource,
    private val bookingRepository: BookingRepository,
    private val accountsRepository: AccountsRepository,
    private val realmRepository: RealmRepository,
    private val authorizationService: AuthorizationService,
    private val budgetRepository: BudgetRepository,
    private val budgetHistoryRepository: BudgetHistoryRepository,
) {

    fun createRapport(userId: String, realmId: String, rangeFilter: DateRangeFilter) = dataSource.readTx { conn ->
        authorizationService.assertAuthorization(
            conn,
            userId,
            realmId,
            AccessRequest.read
        )

        val allAccounts = accountsRepository.getAll(conn, realmId)

        val budgetRules = if (rangeFilter.isMonth()) {
            val requestedMonth = rangeFilter.from.atZone(DateTimeUtils.zoneId).toLocalDate()
            val realm = realmRepository.getAll(conn).single { it.id == realmId }
            val closureDate = LocalDate.of(realm.closedYear, realm.closedMonth, 1)

            if (requestedMonth.isAfter(closureDate)) {
                // get from budget
                budgetRepository.getAll(conn, realmId).filter { it.month == requestedMonth.monthValue }
            } else {
                // get from history
                budgetHistoryRepository.getAll(realmId, requestedMonth.year, requestedMonth.monthValue).map {
                    BudgetDbRecord(
                        realmId,
                        it.accountId,
                        it.month,
                        it.amountInCents
                    )
                }
            }
        } else null

        fun getForAccount(a: AccountDto): OverviewRapportAccount {
            val children = allAccounts.allAccounts
                .filter { it.parentAccountId == a.id }
                .map { getForAccount(it) }

            val openingBalance = bookingRepository.getSum(
                accountId = a.id,
                realmId = realmId,
                dateRangeFilter = DateRangeFilter(toExclusive = rangeFilter.from)
            ).first + children.sumOf { it.openBalance }

            val sum = bookingRepository.getSum(
                accountId = a.id,
                realmId = realmId,
                dateRangeFilter = rangeFilter
            )

            val budget = if (budgetRules != null) {
                val childrenWithBudget = children.filter { it.budget != null }
                val budget = budgetRules.firstOrNull { it.accountId == a.id }
                if (budget != null || childrenWithBudget.isNotEmpty()) {
                    childrenWithBudget.sumOf { it.budget!! } + (budget?.amountInCents ?: 0)
                } else null
            } else null

            val thisPeriod = sum.first + children.sumOf { it.thisPeriod }

            return OverviewRapportAccount(
                accountId = a.id,
                openBalance = openingBalance,
                thisPeriod = thisPeriod,
                closeBalance = openingBalance + thisPeriod,
                children = children,
                deepEntrySize = children.sumOf { it.deepEntrySize } + sum.second,
                budget = budget,
            )
        }

        StandardAccount.entries
            .filter { it.parent == null }
            .map { root -> allAccounts.allAccounts.first { it.id == root.toAccountId(realmId) } }
            .map { getForAccount(it) }
    }

}


data class OverviewRapportAccount(
    val accountId: String,
    val openBalance: Long,
    val thisPeriod: Long,
    val closeBalance: Long,
    val children: List<OverviewRapportAccount>,
    val deepEntrySize: Int,
    val budget: Long?,
)
