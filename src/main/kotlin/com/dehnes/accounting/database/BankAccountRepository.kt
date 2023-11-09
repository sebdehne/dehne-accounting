package com.dehnes.accounting.database

import com.dehnes.accounting.api.BankAccountChanged
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant

class BankAccountRepository(
    private val changelog: Changelog,
) {

    fun getAllBankAccounts(connection: Connection, realmId: String): List<BankAccountDto> = connection.prepareStatement(
        """
        select ba.* from bank_account ba, account a where ba.account_id = a.id and a.realm_id = ?
    """.trimIndent()
    ).use { preparedStatement ->
        preparedStatement.setString(1, realmId)
        preparedStatement.executeQuery().use { rs ->
            val l = mutableListOf<BankAccountDto>()
            while (rs.next()) {
                l.add(
                    BankAccountDto(
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
        bankAccount: BankAccount,
    ) {
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
            preparedStatement.setString(1, bankAccount.accountId)
            preparedStatement.setString(2, bankAccount.bankId)
            preparedStatement.setString(3, bankAccount.accountNumber?.ifBlank { null })
            preparedStatement.setTimestamp(4, Timestamp.from(bankAccount.openDate))
            preparedStatement.setTimestamp(5, bankAccount.closeDate?.let { Timestamp.from(it) })
            preparedStatement.setLong(6, 0)
            preparedStatement.executeUpdate()
        }

        changelog.add(BankAccountChanged)
    }

    fun updateBankAccount(
        connection: Connection,
        bankAccount: BankAccount,
    ) {
        connection.prepareStatement(
            """
            UPDATE 
                bank_account 
            SET 
                bank_id = ?,
                account_number = ?,
                open_date = ?,
                close_date = ?
            WHERE account_id = ?
        """.trimIndent()
        ).use { preparedStatement ->
            preparedStatement.setString(1, bankAccount.bankId)
            preparedStatement.setString(2, bankAccount.accountNumber?.ifBlank { null })
            preparedStatement.setTimestamp(3, Timestamp.from(bankAccount.openDate))
            preparedStatement.setTimestamp(4, bankAccount.closeDate?.let { Timestamp.from(it) })
            preparedStatement.setString(5, bankAccount.accountId)

            preparedStatement.executeUpdate()
        }
        changelog.add(BankAccountChanged)
    }

    fun deleteBankAccount(
        connection: Connection,
        accountId: String,
    ) {
        connection.prepareStatement(
            """
            DELETE FROM bank_account WHERE account_id = ?
        """.trimIndent()
        ).use { preparedStatement ->
            preparedStatement.setString(1, accountId)
            preparedStatement.executeUpdate()
        }

        changelog.add(BankAccountChanged)
    }
}

data class BankAccountDto(
    val accountId: String,
    val bankId: String,
    val accountNumber: String?,
    val openDate: Instant,
    val closeDate: Instant?,
    val lastUnbookedTransactionId: Long,
)

data class BankAccount(
    val accountId: String,
    val bankId: String,
    val accountNumber: String?,
    val openDate: Instant,
    val closeDate: Instant?,
)
