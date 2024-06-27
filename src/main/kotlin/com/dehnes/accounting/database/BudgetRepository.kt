package com.dehnes.accounting.database

import com.dehnes.accounting.api.BudgetChanged
import java.sql.Connection
import javax.sql.DataSource

class BudgetRepository(
    private val changelog: Changelog,
    private val dataSource: DataSource,
) {

    fun getAll(realmId: String) = dataSource.connection.use { connection ->
        connection.autoCommit = false

        connection.prepareStatement("SELECT * FROM budget WHERE realm_id = ?").use { statement ->
            statement.setString(1, realmId)
            statement.executeQuery().use { resultSet ->
                val r = mutableListOf<BudgetDbRecord>()
                while (resultSet.next()) {
                    r.add(
                        BudgetDbRecord(
                            realmId,
                            resultSet.getString("account_id"),
                            resultSet.getInt("month"),
                            resultSet.getLong("amount_in_cents"),
                        )
                    )
                }
                r
            }
        }
    }

    fun insertOrUpdate(connection: Connection, budget: BudgetDbRecord) {
        connection.prepareStatement(
            """
                INSERT INTO budget (
                    realm_id,
                    account_id,
                    month,
                    amount_in_cents
                ) VALUES (?,?,?,?) ON CONFLICT (account_id,month) DO UPDATE SET 
                    amount_in_cents = excluded.amount_in_cents
            """.trimIndent()
        ).use { preparedStatement ->

            preparedStatement.setString(1, budget.realmId)
            preparedStatement.setString(2, budget.accountId)
            preparedStatement.setInt(3, budget.month)
            preparedStatement.setLong(4, budget.amountInCents)

            preparedStatement.executeUpdate()
        }
        changelog.add(BudgetChanged)
    }

    fun insertOrUpdate(budget: BudgetDbRecord) {
        changelog.writeTx { connection ->
            insertOrUpdate(connection, budget)
        }
    }

    fun delete(realmId: String, accountId: String, month: Int) {
        changelog.writeTx { connection ->
            connection.prepareStatement("DELETE FROM budget WHERE realm_id = ? AND account_id = ? AND month = ?")
                .use { statement ->
                    statement.setString(1, realmId)
                    statement.setString(2, accountId)
                    statement.setInt(3, month)
                    statement.executeUpdate()
                }
            changelog.add(BudgetChanged)
        }
    }

    fun mergeAccount(connection: Connection, realmId: String, sourceAccountId: String, targetAccountId: String) {

        val all = getAll(realmId)

        (1..12).forEach { month ->
            val source = all.firstOrNull { it.accountId == sourceAccountId && it.month == month }
            if (source != null) {
                val targetAmountInCents =
                    all.firstOrNull { it.accountId == targetAccountId && it.month == month }?.amountInCents ?: 0L
                insertOrUpdate(
                    connection,
                    BudgetDbRecord(
                        realmId,
                        targetAccountId,
                        month,
                        source.amountInCents + targetAmountInCents
                    )
                )
            }
        }

        connection.prepareStatement("DELETE FROM budget WHERE realm_id = ? AND account_id = ?").use { statement ->
            statement.setString(1, realmId)
            statement.setString(2, sourceAccountId)
            statement.executeUpdate()
        }
    }

}

data class BudgetDbRecord(
    val realmId: String,
    val accountId: String,
    val month: Int,
    val amountInCents: Long,
)