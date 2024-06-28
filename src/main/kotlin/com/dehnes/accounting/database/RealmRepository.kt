package com.dehnes.accounting.database

import com.dehnes.accounting.api.RealmChanged
import com.dehnes.accounting.domain.InformationElement
import com.dehnes.accounting.domain.StandardAccount
import java.sql.Connection
import java.time.LocalDate

class RealmRepository(
    private val accountsRepository: AccountsRepository,
    private val changelog: Changelog,
) {

    fun insert(realm: Realm) {
        changelog.writeTx { conn ->
            conn.prepareStatement(
                """
                INSERT INTO realm (
                    id,
                    name,
                    description,
                    currency,
                    last_booking_id,
                    closed_year,
                    closed_month
                ) VALUES (?,?,?,?,?,?,?)
            """.trimIndent()
            ).use { preparedStatement ->
                preparedStatement.setString(1, realm.id)
                preparedStatement.setString(2, realm.name)
                preparedStatement.setString(3, realm.description)
                preparedStatement.setString(4, realm.currency)
                preparedStatement.setLong(5, 0)
                preparedStatement.setInt(6, realm.closedYear)
                preparedStatement.setInt(7, realm.closedMonth)
                preparedStatement.executeUpdate()
            }

            StandardAccount.entries.forEach { sa ->
                accountsRepository.insert(
                    conn,
                    AccountDto(
                        sa.toAccountId(realm.id),
                        sa.name,
                        null,
                        realm.id,
                        sa.parent?.toAccountId(realm.id),
                        false,
                    )
                )
            }
        }
    }

    fun getNextBookingId(connection: Connection, realmId: String): Long {
        val lastBookingId =
            connection.prepareStatement("SELECT last_booking_id from realm where id = ?").use { preparedStatement ->
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

    fun getAll(connection: Connection): List<Realm> =
        connection.prepareStatement("SELECT * FROM realm").use { preparedStatement ->
            preparedStatement.executeQuery().use { rs ->
                val l = mutableListOf<Realm>()
                while (rs.next()) {
                    l.add(
                        Realm(
                            id = rs.getString("id"),
                            name = rs.getString("name"),
                            description = rs.getString("description"),
                            currency = rs.getString("currency"),
                            lastBookingId = rs.getLong("last_booking_id"),
                            closedYear = rs.getInt("closed_year"),
                            closedMonth = rs.getInt("closed_month")
                        )
                    )
                }
                l
            }
        }

    fun updateClosure(connection: Connection, realmId: String, direction: CloseDirection) {

        val realm = getAll(connection).firstOrNull { it.id == realmId } ?: error("Fant ikke realmId=$realmId")

        val closeDate = LocalDate.of(
            realm.closedYear,
            realm.closedMonth,
            1
        )
        val updatedDate = when (direction) {
            CloseDirection.forward -> closeDate.plusMonths(1)
            CloseDirection.backwards -> closeDate.plusMonths(-1)
        }

        connection.prepareStatement("UPDATE realm set closed_year = ?, closed_month = ? WHERE id = ?")
            .use { preparedStatement ->
                preparedStatement.setInt(1, updatedDate.year)
                preparedStatement.setInt(2, updatedDate.monthValue)
                preparedStatement.setString(3, realmId)
                preparedStatement.executeUpdate()
            }

        changelog.add(RealmChanged)
    }

    enum class CloseDirection {
        forward,
        backwards
    }

}

data class Realm(
    override val id: String,
    override val name: String,
    override val description: String?,
    val currency: String,
    val lastBookingId: Long,
    val closedYear: Int,
    val closedMonth: Int,
) : InformationElement()

