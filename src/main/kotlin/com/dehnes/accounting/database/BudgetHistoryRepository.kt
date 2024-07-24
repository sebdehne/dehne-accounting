package com.dehnes.accounting.database

import java.sql.Connection
import java.time.LocalDate
import javax.sql.DataSource

class BudgetHistoryRepository(
    private val dataSource: DataSource,
    private val changelog: Changelog,
) {

    fun getAll(realmId: String, year: Int, month: Int): List<BudgetHistory> = dataSource.connection.use { c ->
        c.autoCommit = false

        c.prepareStatement("SELECT * FROM budget_history WHERE realm_id = ? AND year = ? AND month = ?")
            .use { preparedStatement ->
                preparedStatement.setString(1, realmId)
                preparedStatement.setInt(2, year)
                preparedStatement.setInt(3, month)
                preparedStatement.executeQuery().use {
                    val r = mutableListOf<BudgetHistory>()

                    while (it.next()) {
                        r.add(
                            BudgetHistory(
                                realmId = realmId,
                                year = year,
                                month = month,
                                accountId = it.getString("account_id"),
                                amountInCents = it.getLong("amount_in_cents")
                            )
                        )
                    }

                    r
                }
            }
    }

    fun insertOrUpdate(connection: Connection, budgetHistory: BudgetHistory) {
        connection.prepareStatement(
            """
                INSERT INTO budget_history (
                    realm_id,
                    year,
                    month,
                    account_id,
                    amount_in_cents
                ) VALUES (?,?,?,?,?) ON CONFLICT (realm_id, year, month, account_id) DO UPDATE SET 
                    amount_in_cents = excluded.amount_in_cents
            """.trimIndent()
        ).use { preparedStatement ->

            preparedStatement.setString(1, budgetHistory.realmId)
            preparedStatement.setInt(2, budgetHistory.year)
            preparedStatement.setInt(3, budgetHistory.month)
            preparedStatement.setString(4, budgetHistory.accountId)
            preparedStatement.setLong(5, budgetHistory.amountInCents)
            preparedStatement.executeUpdate()
        }
    }

    fun delete(realmId: String, year: Int, month: Int, accountId: String) {
        changelog.writeTx { connection ->

            connection.prepareStatement(
                """
                DELETE FROM budget_history WHERE 
                    realm_id = ?
                    AND year = ?
                    AND month = ? 
                    AND account_id = ?
            """.trimIndent()
            )
                .use { preparedStatement ->
                    preparedStatement.setString(1, realmId)
                    preparedStatement.setInt(2, year)
                    preparedStatement.setInt(3, month)
                    preparedStatement.setString(4, accountId)
                    preparedStatement.executeUpdate()
                }
        }
    }

    fun merge(connection: Connection, realmId: String, sourceAccountId: String, targetAccountId: String) {
        val sourceRecords = mutableListOf<Pair<LocalDate, Long>>()
        connection.prepareStatement("SELECT * FROM budget_history WHERE realm_id = ? AND account_id = ?")
            .use { preparedStatement ->
                preparedStatement.setString(1, realmId)
                preparedStatement.setString(2, sourceAccountId)
                preparedStatement.executeQuery().use { rs ->
                    while (rs.next()) {
                        sourceRecords.add(
                            LocalDate.of(
                                rs.getInt("year"),
                                rs.getInt("month"),
                                1
                            ) to rs.getLong("amount_in_cents")
                        )
                    }
                }
            }

        sourceRecords.forEach { (date, amount) ->
            connection.prepareStatement(
                """
                UPDATE budget_history set 
                amount_in_cents = amount_in_cents + ?
                WHERE realm_id = ? AND year = ? AND month = ? AND account_id = ?
            """.trimIndent()
            ).use { preparedStatement ->
                preparedStatement.setLong(1, amount)
                preparedStatement.setString(2, realmId)
                preparedStatement.setInt(3, date.year)
                preparedStatement.setInt(4, date.monthValue)
                preparedStatement.setString(5, targetAccountId)
                preparedStatement.executeUpdate()
            }
        }

        connection.prepareStatement("DELETE FROM budget_history WHERE realm_id = ? AND account_id = ?")
            .use { statement ->
                statement.setString(1, realmId)
                statement.setString(2, sourceAccountId)
                statement.executeUpdate()
            }

    }


}

data class BudgetHistory(
    val realmId: String,
    val year: Int,
    val month: Int,
    val accountId: String,
    val amountInCents: Long,
)
