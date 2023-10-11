package com.dehnes.accounting.database

import com.dehnes.accounting.database.Transactions.readTx
import com.dehnes.accounting.database.Transactions.writeTx
import java.sql.Connection
import javax.sql.DataSource

class BankRepository(
    private val dataSource: DataSource
) {

    fun getByName(connection: Connection, name: String) = connection.prepareStatement("SELECT * FROM bank WHERE name = ?").use { preparedStatement ->
        preparedStatement.setString(1, name)
        preparedStatement.executeQuery().use { rs ->
            if (rs.next()) BankDto(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("transaction_import_function")
            ) else null
        }
    }

    fun insert(bank: BankDto) {
        dataSource.writeTx { conn ->
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