package com.dehnes.accounting.database

import com.dehnes.accounting.api.AccountsChanged
import com.dehnes.accounting.domain.InformationElement
import com.dehnes.accounting.domain.StandardAccount
import io.github.oshai.kotlinlogging.KotlinLogging
import java.sql.Connection

class AccountsRepository(private val changelog: Changelog) {

    private val logger = KotlinLogging.logger { }

    fun getAll(conn: Connection, realmId: String): AllAccounts {
        val accountDtos = conn.prepareStatement(
            "SELECT * FROM account where realm_id = ?"
        ).use { preparedStatement ->
            preparedStatement.setString(1, realmId)
            preparedStatement.executeQuery().use { rs ->
                val l = mutableListOf<AccountDto>()
                while (rs.next()) {
                    val accountId = rs.getString("id")
                    l.add(
                        AccountDto(
                            id = accountId,
                            name = rs.getString("name"),
                            description = rs.getString("description"),
                            realmId = realmId,
                            parentAccountId = rs.getString("parent_account_id"),
                            builtIn = StandardAccount.entries.any { it.toAccountId(realmId) == accountId },
                            budgetType = rs.getString("budget_type")?.let { BudgetType.valueOf(it) },
                        )
                    )
                }
                l
            }
        }

        return AllAccounts(
            accountDtos,
            StandardAccount.entries.map {
                StandardAccountView(
                    it.toAccountId(realmId),
                    it.name,
                    it.parent?.toAccountId(realmId),
                    it
                )
            }
        )
    }

    fun insert(accountDto: AccountDto) {
        changelog.writeTx { conn ->
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
                    parent_account_id,
                    budget_type
                ) VALUES (?,?,?,?,?,?)
            """.trimIndent()
        ).use { preparedStatement ->
            preparedStatement.setString(1, accountDto.realmId)
            preparedStatement.setString(2, accountDto.id)
            preparedStatement.setString(3, accountDto.name)
            preparedStatement.setString(4, accountDto.description)
            preparedStatement.setString(5, accountDto.parentAccountId)
            preparedStatement.setString(6, accountDto.budgetType?.name?.ifBlank { null })
            preparedStatement.executeUpdate()
            logger.info { "Inserted account $accountDto" }
        }

        changelog.add(AccountsChanged)
    }

    fun updateAccount(conn: Connection, accountDto: AccountDto) {
        conn.prepareStatement(
            """
            UPDATE account set parent_account_id = ?, name = ?, description = ?, budget_type = ? WHERE id = ?
        """.trimIndent()
        ).use { preparedStatement ->
            preparedStatement.setString(1, accountDto.parentAccountId)
            preparedStatement.setString(2, accountDto.name)
            preparedStatement.setString(3, accountDto.description)
            preparedStatement.setString(4, accountDto.budgetType?.name?.ifBlank { null })
            preparedStatement.setString(5, accountDto.id)
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
    val budgetType: BudgetType?,
) : InformationElement()

data class AllAccounts(
    val allAccounts: List<AccountDto>,
    val standardAccounts: List<StandardAccountView>,
)

data class StandardAccountView(
    val id: String,
    val originalName: String,
    val parentAccountId: String?,
    val standardAccount: StandardAccount,
)
