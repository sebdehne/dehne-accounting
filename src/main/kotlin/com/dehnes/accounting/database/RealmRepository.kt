package com.dehnes.accounting.database

import com.dehnes.accounting.database.Transactions.writeTx
import com.dehnes.accounting.domain.InformationElement
import com.dehnes.accounting.domain.StandardAccount
import java.sql.Connection
import javax.sql.DataSource

class RealmRepository(
    private val dataSource: DataSource,
    private val accountsRepository: AccountsRepository,
) {

    fun insert(realm: Realm) {
        dataSource.writeTx { conn ->
            conn.prepareStatement("""
                INSERT INTO realm (
                    id,
                    name,
                    description,
                    currency,
                    last_booking_id
                ) VALUES (?,?,?,?,?)
            """.trimIndent()).use { preparedStatement ->
                preparedStatement.setString(1, realm.id)
                preparedStatement.setString(2, realm.name)
                preparedStatement.setString(3, realm.description)
                preparedStatement.setString(4, realm.currency)
                preparedStatement.setLong(5, 0)
                preparedStatement.executeUpdate()
            }

            StandardAccount.entries.forEach { sa ->
                accountsRepository.insert(conn, AccountDto(
                    sa.toAccountId(realm.id),
                    sa.name,
                    null,
                    realm.id,
                    sa.parent?.toAccountId(realm.id),
                    null,
                ))
            }
        }
    }

    fun getNextBookingId(connection: Connection, realmId: String): Long {
        val lastBookingId = connection.prepareStatement("SELECT last_booking_id from realm where id = ?").use { preparedStatement ->
            preparedStatement.setString(1, realmId)
            preparedStatement.executeQuery().use { rs ->
                check(rs.next())
                rs.getLong("last_booking_id")
            }
        }

        connection.prepareStatement("UPDATE realm set last_booking_id = ? WHERE id =  ?").use { preparedStatement ->
            preparedStatement.setLong(1, lastBookingId + 1)
            preparedStatement.setString(2, realmId)
            preparedStatement.executeUpdate()
        }

        return lastBookingId + 1
    }

}

data class Realm(
    override val id: String,
    override val name: String,
    override val description: String?,
    val currency: String,
    val lastBookingId: Long,
): InformationElement()

