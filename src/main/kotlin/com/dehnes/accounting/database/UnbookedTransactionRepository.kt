package com.dehnes.accounting.database

import com.dehnes.accounting.utils.NullString
import com.dehnes.accounting.utils.SqlUtils
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant

class UnbookedTransactionRepository {

    fun insert(conn: Connection, unbookedTransaction: UnbookedTransaction): Long {

        val previousId =
            conn.prepareStatement("SELECT last_unbooked_transaction_id FROM bank_account where account_id = ?").use {
                it.setString(1, unbookedTransaction.accountId)
                it.executeQuery().use {
                    check(it.next())
                    it.getLong("last_unbooked_transaction_id")
                }
            }

        val newId = previousId + 1

        conn.prepareStatement(
            """
            INSERT INTO unbooked_bank_transaction (account_id, id, memo, datetime, amount_in_cents, other_account_number) VALUES (?,?,?,?,?,?)
        """.trimIndent()
        ).use { preparedStatement ->
            val params = mutableListOf<Any>()
            params.add(unbookedTransaction.accountId)
            params.add(newId)
            params.add(unbookedTransaction.memo ?: NullString)
            params.add(unbookedTransaction.datetime)
            params.add(unbookedTransaction.amountInCents)
            params.add(unbookedTransaction.otherAccountNumber ?: NullString)

            SqlUtils.setSqlParams(preparedStatement, params)

            preparedStatement.executeUpdate()
        }

        conn.prepareStatement("UPDATE bank_account set last_unbooked_transaction_id = ? WHERE account_id = ?").use {
            it.setLong(1, newId)
            it.setString(2, unbookedTransaction.accountId)
            it.executeUpdate()
        }

        return newId
    }

    fun getLastKnownUnbookedTransactionDate(conn: Connection, accountId: String): Instant? =
        conn.prepareStatement(
            "SELECT max(datetime) from unbooked_bank_transaction where account_id = ?"
        ).use { preparedStatement ->
            preparedStatement.setString(1, accountId)
            preparedStatement.executeQuery().use { rs ->
                check(rs.next())
                rs.getTimestamp(1)?.toInstant()
            }
        }

    fun getSum(
        conn: Connection,
        accountId: String,
        rangeFilter: BankTxDateRangeFilter
    ): Long {
        val (where, whereParams) = rangeFilter.whereAndParams()
        return conn.prepareStatement("""
            SELECT 
                sum(amount_in_cents) 
            from 
                unbooked_bank_transaction 
            where 
                account_id = ? AND $where
        """.trimIndent()).use { preparedStatement ->
            val params = mutableListOf<Any>()
            params.add(accountId)
            params.addAll(whereParams)
            SqlUtils.setSqlParams(preparedStatement, params)
            preparedStatement.executeQuery().use { rs ->
                check(rs.next())
                rs.getLong(1)
            }
        }
    }

    fun getUnbookedTransactions(
        conn: Connection,
        accountId: String,
        dateRangeFilter: DateRangeFilter
    ): List<UnbookedTransaction> =
        conn.prepareStatement("SELECT * FROM unbooked_bank_transaction WHERE account_id = ? AND datetime >= ? AND datetime < ?")
            .use { preparedStatement ->
                preparedStatement.setString(1, accountId)
                preparedStatement.setTimestamp(2, Timestamp.from(dateRangeFilter.from))
                preparedStatement.setTimestamp(3, Timestamp.from(dateRangeFilter.toExclusive))
                preparedStatement.executeQuery().use { rs ->
                    val l = mutableListOf<UnbookedTransaction>()
                    while (rs.next()) {
                        l.add(
                            UnbookedTransaction(
                                rs.getString("account_id"),
                                rs.getLong("id"),
                                rs.getString("memo"),
                                rs.getTimestamp("datetime").toInstant(),
                                rs.getLong("amount_in_cents"),
                                rs.getString("other_account_number")
                            )
                        )
                    }
                    l
                }
            }

}

data class UnbookedTransaction(
    val accountId: String,
    val id: Long,
    val memo: String?,
    val datetime: Instant,
    val amountInCents: Long,
    val otherAccountNumber: String?,
)