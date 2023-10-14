package com.dehnes.accounting.database

import com.dehnes.accounting.api.AccountsChanged
import com.dehnes.accounting.database.Transactions.writeTx
import com.dehnes.accounting.domain.InformationElement
import mu.KotlinLogging
import java.lang.Exception
import java.sql.Connection
import javax.sql.DataSource

class AccountsRepository(
    private val dataSource: DataSource,
    private val changelog: Changelog,
) {

    private val logger = KotlinLogging.logger {  }

    fun getAll(conn: Connection, realmId: String): List<AccountDto> = conn.prepareStatement(
        """
        SELECT * FROM account where realm_id = ?
    """.trimIndent()
    ).use { preparedStatement ->
        preparedStatement.setString(1, realmId)
        preparedStatement.executeQuery().use { rs ->
            val l = mutableListOf<AccountDto>()
            while (rs.next()) {
                l.add(
                    AccountDto(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        realmId,
                        rs.getString("parent_account_id"),
                        rs.getString("party_id"),
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
        try {
            conn.prepareStatement(
                """
                INSERT INTO account (
                    realm_id,
                    id,
                    name,
                    description,
                    parent_account_id,
                    party_id
                ) VALUES (?,?,?,?,?,?)
            """.trimIndent()
            ).use { preparedStatement ->
                preparedStatement.setString(1, accountDto.realmId)
                preparedStatement.setString(2, accountDto.id)
                preparedStatement.setString(3, accountDto.name)
                preparedStatement.setString(4, accountDto.description)
                preparedStatement.setString(5, accountDto.parentAccountId)
                preparedStatement.setString(6, accountDto.partyId)
                preparedStatement.executeUpdate()
                logger.info { "Inserted account $accountDto" }
            }
        } catch (e: Exception) {
            throw e
        }

        changelog.addV2(AccountsChanged)
    }

    fun updateAccount(conn: Connection, accountDto: AccountDto) {
        conn.prepareStatement("""
            UPDATE account set parent_account_id = ?, name = ?, description = ?, party_id = ? WHERE id = ?
        """.trimIndent()).use { preparedStatement ->
            preparedStatement.setString(1, accountDto.parentAccountId)
            preparedStatement.setString(2, accountDto.name)
            preparedStatement.setString(3, accountDto.description)
            preparedStatement.setString(4, accountDto.partyId)
            preparedStatement.setString(5, accountDto.id)
            preparedStatement.executeUpdate()
        }

        changelog.addV2(AccountsChanged)
    }

}

data class AccountDto(
    override val id: String,
    override val name: String,
    override val description: String?,
    val realmId: String,
    val parentAccountId: String?,
    val partyId: String?,
) : InformationElement()

