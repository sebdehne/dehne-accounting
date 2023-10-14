package com.dehnes.accounting.database

import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant

class BankAccountRepository {

    fun getAllBankAccounts(connection: Connection, realmId: String): List<BankAccount> = connection.prepareStatement(
        """
        select ba.* from bank_account ba, account a where ba.account_id = a.id and a.realm_id = ?
    """.trimIndent()
    ).use { preparedStatement ->
        preparedStatement.setString(1, realmId)
        preparedStatement.executeQuery().use { rs ->
            val l = mutableListOf<BankAccount>()
            while (rs.next()) {
                l.add(
                    BankAccount(
                        rs.getString("account_id"),
                        rs.getString("bank_id"),
                        rs.getString("account_number"),
                        rs.getTimestamp("open_date").toInstant(),
                        rs.getTimestamp("close_date")?.toInstant(),
                        rs.getLong("last_unbooked_transaction_id"),
                    )
                )
            }
            l
        }
    }

    fun insertBankAccount(
        connection: Connection,
        accountDto: AccountDto,
        bankId: String,
        accountNumber: String?,
        openDate: Instant,
        closeDate: Instant?,
    ): BankAccount {
        connection.prepareStatement(
            """
            INSERT INTO bank_account (
                account_id, 
                bank_id, 
                account_number, 
                open_date, 
                close_date, 
                last_unbooked_transaction_id
            ) VALUES (?,?,?,?,?,?) 
        """.trimIndent()
        ).use { preparedStatement ->
            preparedStatement.setString(1, accountDto.id)
            preparedStatement.setString(2, bankId)
            preparedStatement.setString(3, accountNumber)
            preparedStatement.setTimestamp(4, Timestamp.from(openDate))
            preparedStatement.setTimestamp(5, closeDate?.let { Timestamp.from(it) })
            preparedStatement.setLong(6, 0)
            preparedStatement.executeUpdate()
        }

        return BankAccount(
            accountDto.id,
            bankId,
            accountNumber,
            openDate,
            closeDate,
            0
        )
    }

}

data class BankAccount(
    val accountId: String,
    val bankId: String,
    val accountNumber: String?,
    val openDate: Instant,
    val closeDate: Instant?,
    val lastUnbookedTransactionId: Long,
)

