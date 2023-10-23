package com.dehnes.accounting.database

import com.dehnes.accounting.api.AccountsChanged
import com.dehnes.accounting.database.Transactions.writeTx
import com.dehnes.accounting.domain.InformationElement
import com.dehnes.accounting.domain.StandardAccount
import mu.KotlinLogging
import java.sql.Connection
import javax.sql.DataSource

class AccountsRepository(
    private val dataSource: DataSource,
    private val changelog: Changelog,
) {

    private val logger = KotlinLogging.logger {  }

    fun getAll(conn: Connection, realmId: String): List<AccountDto> = conn.prepareStatement(
        "SELECT * FROM account where realm_id = ?"
    ).use { preparedStatement ->
        preparedStatement.setString(1, realmId)
        preparedStatement.executeQuery().use { rs ->
            val l = mutableListOf<AccountDto>()
            while (rs.next()) {
                val accountId = rs.getString("id")
                l.add(
                    AccountDto(
                        accountId,
                        rs.getString("name"),
                        rs.getString("description"),
                        realmId,
                        rs.getString("parent_account_id"),
                        StandardAccount.entries.any { it.toAccountId(realmId) == accountId }
                    )
                )
            }
            l
        }
    }

    fun insert(accountDto: AccountDto) {
        dataSource.writeTx { conn ->
            insert(conn, accountDto)
        }
    }

    fun insert(conn: Connection, accountDto: AccountDto) {
        conn.prepareStatement(
            """
                INSERT INTO account (
                    realm_id,
                    id,
                    name,
                    description,
                    parent_account_id
                ) VALUES (?,?,?,?,?)
            """.trimIndent()
        ).use { preparedStatement ->
            preparedStatement.setString(1, accountDto.realmId)
            preparedStatement.setString(2, accountDto.id)
            preparedStatement.setString(3, accountDto.name)
            preparedStatement.setString(4, accountDto.description)
            preparedStatement.setString(5, accountDto.parentAccountId)
            preparedStatement.executeUpdate()
            logger.info { "Inserted account $accountDto" }
        }

        changelog.add(AccountsChanged)
    }

    fun updateAccount(conn: Connection, accountDto: AccountDto) {
        conn.prepareStatement("""
            UPDATE account set parent_account_id = ?, name = ?, description = ? WHERE id = ?
        """.trimIndent()).use { preparedStatement ->
            preparedStatement.setString(1, accountDto.parentAccountId)
            preparedStatement.setString(2, accountDto.name)
            preparedStatement.setString(3, accountDto.description)
            preparedStatement.setString(4, accountDto.id)
            preparedStatement.executeUpdate()
        }

        changelog.add(AccountsChanged)
    }

    fun remove(conn: Connection, accountId: String) {
        conn.prepareStatement("delete from account where id = ?").use { preparedStatement ->
            preparedStatement.setString(1, accountId)
            preparedStatement.executeUpdate()
        }
        changelog.add(AccountsChanged)
    }

}

data class AccountDto(
    override val id: String,
    override val name: String,
    override val description: String?,
    val realmId: String,
    val parentAccountId: String?,
    val builtIn: Boolean,
) : InformationElement()

