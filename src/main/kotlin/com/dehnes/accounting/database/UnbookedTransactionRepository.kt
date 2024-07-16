package com.dehnes.accounting.database

import com.dehnes.accounting.api.UnbookedTransactionsChanged
import com.dehnes.accounting.utils.NullString
import com.dehnes.accounting.utils.SqlUtils
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant

class UnbookedTransactionRepository(
    private val changelog: Changelog
) {

    fun deleteAll(conn: Connection, accountId: String) {
        conn.prepareStatement(
            "delete from unbooked_bank_transaction where account_id = ?"
        ).use { preparedStatement ->
            preparedStatement.setString(1, accountId)
            preparedStatement.executeUpdate()
        }
        changelog.add(UnbookedTransactionsChanged)
    }

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
            INSERT INTO unbooked_bank_transaction (account_id, realm_id, id, memo, datetime, amount_in_cents, other_account_number) VALUES (?,?,?,?,?,?,?)
        """.trimIndent()
        ).use { preparedStatement ->
            val params = mutableListOf<Any>()
            params.add(unbookedTransaction.accountId)
            params.add(unbookedTransaction.realmId)
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

        changelog.add(UnbookedTransactionsChanged)

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
        return conn.prepareStatement(
            """
            SELECT 
                sum(amount_in_cents) 
            from 
                unbooked_bank_transaction 
            where 
                account_id = ? AND $where
        """.trimIndent()
        ).use { preparedStatement ->
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

    fun getCount(
        conn: Connection,
        realmId: String,
        accountId: String?,
    ): Long = conn.prepareStatement(
        """
        SELECT 
            count(*) 
        from 
            unbooked_bank_transaction 
        where realm_id = ? ${if (accountId != null) " AND account_id = ?" else ""} 
    """.trimIndent()
    ).use { preparedStatement ->
        preparedStatement.setString(1, realmId)
        if (accountId != null)
            preparedStatement.setString(2, accountId)

        preparedStatement.executeQuery().use { rs ->
            check(rs.next())
            rs.getLong(1)
        }
    }

    fun hasUnbookedTransaction(
        conn: Connection,
        realmId: String,
        from: Instant,
        toExcluding: Instant,
    ): Boolean =
        conn.prepareStatement("SELECT count(*) from unbooked_bank_transaction where realm_id = ? AND datetime >= ? AND datetime < ?")
            .use { preparedStatement ->
                preparedStatement.setString(1, realmId)
                preparedStatement.setTimestamp(2, Timestamp.from(from))
                preparedStatement.setTimestamp(3, Timestamp.from(toExcluding))
                preparedStatement.executeQuery().use { rs ->
                    check(rs.next())
                    val long = rs.getLong(1)
                    long > 0
                }
            }

    fun getUnbookedTransactions(
        conn: Connection,
        realmId: String,
        accountId: String,
        dateRangeFilter: DateRangeFilter
    ): List<UnbookedTransaction> =
        conn.prepareStatement(
            """
            SELECT * 
            FROM 
                unbooked_bank_transaction
            WHERE realm_id = ? 
                AND account_id = ? 
                AND datetime >= ? AND datetime < ?
        """.trimIndent()
        )
            .use { preparedStatement ->
                preparedStatement.setString(1, realmId)
                preparedStatement.setString(2, accountId)
                preparedStatement.setTimestamp(3, Timestamp.from(dateRangeFilter.from))
                preparedStatement.setTimestamp(4, Timestamp.from(dateRangeFilter.toExclusive))
                preparedStatement.executeQuery().use { rs ->
                    val l = mutableListOf<UnbookedTransaction>()
                    while (rs.next()) {
                        l.add(
                            UnbookedTransaction(
                                rs.getString("account_id"),
                                rs.getString("realm_id"),
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

    fun getUnbookedTransaction(
        conn: Connection,
        realmId: String,
        accountId: String,
        unbookedTransactionId: Long,
    ) =
        conn.prepareStatement(
            """
            SELECT * 
            FROM 
                unbooked_bank_transaction ut
            WHERE realm_id = ?
                AND account_id = ? 
                AND id = ? 
        """.trimIndent()
        )
            .use { preparedStatement ->
                preparedStatement.setString(1, realmId)
                preparedStatement.setString(2, accountId)
                preparedStatement.setLong(3, unbookedTransactionId)
                preparedStatement.executeQuery().use { rs ->
                    if (rs.next()) {
                        UnbookedTransaction(
                            rs.getString("account_id"),
                            rs.getString("realm_id"),
                            rs.getLong("id"),
                            rs.getString("memo"),
                            rs.getTimestamp("datetime").toInstant(),
                            rs.getLong("amount_in_cents"),
                            rs.getString("other_account_number")
                        )
                    } else {
                        null
                    }
                }
            }

    fun delete(conn: Connection, accountId: String, id: Long) {
        conn.prepareStatement("DELETE FROM unbooked_bank_transaction WHERE account_id = ? AND id = ?")
            .use { preparedStatement ->
                preparedStatement.setString(1, accountId)
                preparedStatement.setLong(2, id)
                preparedStatement.executeUpdate()
            }

        changelog.add(UnbookedTransactionsChanged)
    }

}

data class UnbookedTransaction(
    val accountId: String,
    val realmId: String,
    val id: Long,
    val memo: String?,
    val datetime: Instant,
    val amountInCents: Long,
    val otherAccountNumber: String?,
)

interface QueryFilter {
    fun whereAndParams(): Pair<String, List<Any>>
}

interface BookingsFilter : QueryFilter
interface BankAccountTransactionsFilter : QueryFilter

data class DateRangeFilter(
    val from: Instant = Instant.MIN,
    val toExclusive: Instant = Instant.MAX,
) : BookingsFilter {
    override fun whereAndParams(): Pair<String, List<Any>> {
        return "b.datetime >= ? AND b.datetime < ?" to listOf(
            from,
            toExclusive
        )
    }
}

class AccountIdFilter(
    val accountId: String,
    private val realmId: String,
) : BookingsFilter {
    override fun whereAndParams(): Pair<String, List<Any>> =
        "b.id in (SELECT distinct booking_id from booking_entry WHERE realm_id = ? AND account_id = ?)" to listOf(
            realmId,
            accountId
        )
}

class SingleBookingFilter(
    val bookingId: Long,
) : BookingsFilter {
    override fun whereAndParams(): Pair<String, List<Any>> =
        "b.id = ?" to listOf(bookingId)
}

class BankTxDateRangeFilter(
    private val from: Instant = Instant.MIN,
    private val toExclusive: Instant = Instant.MAX,
) : BankAccountTransactionsFilter {
    override fun whereAndParams(): Pair<String, List<Any>> {
        return "datetime >= ? AND datetime < ?" to listOf(
            from,
            toExclusive
        )
    }
}