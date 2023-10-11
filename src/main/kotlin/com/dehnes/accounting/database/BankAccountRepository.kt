package com.dehnes.accounting.database

import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

class BankAccountRepository(
    private val dataSource: DataSource,
    private val accountsRepository: AccountsRepository
) {

    fun insert(
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

