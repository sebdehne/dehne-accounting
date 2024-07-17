package com.dehnes.accounting.services

import com.dehnes.accounting.database.AccountDto
import com.dehnes.accounting.database.AccountsRepository
import com.dehnes.accounting.database.BookingRepository
import com.dehnes.accounting.database.DateRangeFilter
import com.dehnes.accounting.database.Transactions.readTx
import com.dehnes.accounting.domain.StandardAccount
import com.dehnes.accounting.utils.Metrics.logTimed
import javax.sql.DataSource


class OverviewRapportService(
    private val dataSource: DataSource,
    private val bookingRepository: BookingRepository,
    private val accountsRepository: AccountsRepository,
) {

    fun createRapport(realmId: String, rangeFilter: DateRangeFilter) =
        dataSource.readTx { conn ->
            val allAccounts = accountsRepository.getAll(conn, realmId)

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

                val thisPeriod = sum.first + children.sumOf { it.thisPeriod }

                return OverviewRapportAccount(
                    accountId = a.id,
                    name = a.name,
                    openBalance = openingBalance,
                    thisPeriod = thisPeriod,
                    closeBalance = openingBalance + thisPeriod,
                    children = children,
                    deepEntrySize = children.sumOf { it.deepEntrySize } + sum.second
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
    val name: String,
    val openBalance: Long,
    val thisPeriod: Long,
    val closeBalance: Long,
    val children: List<OverviewRapportAccount>,
    val deepEntrySize: Int,
)
