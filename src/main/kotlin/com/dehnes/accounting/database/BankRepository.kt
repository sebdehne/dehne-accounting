package com.dehnes.accounting.database

import com.dehnes.accounting.domain.InformationElement
import java.sql.Connection
import javax.sql.DataSource

class BankRepository(
    private val dataSource: DataSource,
    private val changelog: Changelog,
) {

    fun getByName(connection: Connection, name: String) = getAll(connection).firstOrNull { it.name == name }

    fun getAll(connection: Connection) =
        connection.prepareStatement("SELECT * FROM bank").use { preparedStatement ->
            preparedStatement.executeQuery().use { rs ->
                val l = mutableListOf<BankDto>()
                while (rs.next()) l.add(
                    BankDto(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getString("transaction_import_function")
                    )
                )
                l
            }
        }

    fun insert(bank: BankDto) {
        changelog.writeTx { conn ->
            insert(conn, bank)
        }
    }

    fun insert(connection: Connection, bank: BankDto) {
        connection.prepareStatement("INSERT INTO bank (id, name, description, transaction_import_function) VALUES (?,?,?,?)")
            .use { preparedStatement ->
                preparedStatement.setString(1, bank.id)
                preparedStatement.setString(2, bank.name)
                preparedStatement.setString(3, bank.description)
                preparedStatement.setString(4, bank.transactionImportFunction)
                preparedStatement.executeUpdate()
            }
    }

}

data class BankDto(
    override val id: String,
    override val name: String,
    override val description: String?,
    val transactionImportFunction: String?,
) : InformationElement()
