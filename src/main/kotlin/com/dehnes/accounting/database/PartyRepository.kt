package com.dehnes.accounting.database

import com.dehnes.accounting.database.Transactions.writeTx
import com.dehnes.accounting.domain.InformationElement
import mu.KotlinLogging
import java.lang.Exception
import java.sql.Connection
import javax.sql.DataSource

class PartyRepository(
    private val dataSource: DataSource
) {

    private val logger = KotlinLogging.logger {  }
    fun insert(party: Party) {
        dataSource.writeTx { conn ->
            insert(conn, party)
        }
    }

    fun get(connection: Connection, partyId: String) = connection.prepareStatement("select * from party where id = ?").use { preparedStatement ->
        preparedStatement.setString(1, partyId)
        preparedStatement.executeQuery().use { rs ->
            if (rs.next()) {
                Party(
                    rs.getString("id"),
                    rs.getString("name"),
                    rs.getString("description"),
                    rs.getString("realm_id"),
                )
            } else null
        }
    }

    fun insert(connection: Connection, party: Party) {
        connection.prepareStatement("""
            insert into party (realm_id, id, name, description) VALUES (?,?,?,?)
        """.trimIndent()).use { preparedStatement ->
            preparedStatement.setString(1, party.realmId)
            preparedStatement.setString(2, party.id)
            preparedStatement.setString(3, party.name)
            preparedStatement.setString(4, party.description)
            preparedStatement.executeUpdate()
            logger.info { "Imported part=$party" }
        }
    }

}

data class Party(
    override val id: String,
    override val name: String,
    override val description: String?,
    val realmId: String,
): InformationElement()