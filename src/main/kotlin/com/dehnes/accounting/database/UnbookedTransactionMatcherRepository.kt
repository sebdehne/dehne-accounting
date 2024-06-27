package com.dehnes.accounting.database

import com.dehnes.accounting.api.UnbookedTransactionMatchersChanged
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant

class UnbookedBankTransactionMatcherRepository(
    private val objectMapper: ObjectMapper,
    private val changelog: Changelog,
) {

    fun insert(connection: Connection, matcher: UnbookedBankTransactionMatcher) {
        connection.prepareStatement("INSERT INTO unbooked_bank_transaction_matcher (realm_id, id, json, last_used) VALUES (?,?,?,?)")
            .use { preparedStatement ->
                preparedStatement.setString(1, matcher.realmId)
                preparedStatement.setString(2, matcher.id)
                preparedStatement.setString(3, objectMapper.writeValueAsString(matcher))
                preparedStatement.setTimestamp(4, Timestamp.from(Instant.now()))
                preparedStatement.executeUpdate()
            }

        changelog.add(UnbookedTransactionMatchersChanged)
    }

    fun update(connection: Connection, matcher: UnbookedBankTransactionMatcher): Boolean {
        val updated = connection.prepareStatement(
            """
            UPDATE unbooked_bank_transaction_matcher set json = ?, last_used = ? WHERE id = ?
        """.trimIndent()
        ).use { preparedStatement ->
            preparedStatement.setString(1, objectMapper.writeValueAsString(matcher))
            preparedStatement.setTimestamp(2, Timestamp.from(matcher.lastUsed))
            preparedStatement.setString(3, matcher.id)
            preparedStatement.executeUpdate() > 0
        }
        if (updated) {
            changelog.add(UnbookedTransactionMatchersChanged)
        }
        return updated
    }

    fun getAll(connection: Connection, realmId: String) = connection.prepareStatement(
        "SELECT * FROM unbooked_bank_transaction_matcher WHERE realm_id = ?"
    ).use { preparedStatement ->
        preparedStatement.setString(1, realmId)
        preparedStatement.executeQuery().use { rs ->
            val l = mutableListOf<UnbookedBankTransactionMatcher>()
            while (rs.next()) {
                l.add(objectMapper.readValue(rs.getString("json")))
            }
            l
        }
    }

    fun remove(conn: Connection, matcherId: String, realmId: String) {
        conn.prepareStatement("DELETE FROM unbooked_bank_transaction_matcher where  id = ? AND realm_id = ?")
            .use { preparedStatement ->
                preparedStatement.setString(1, matcherId)
                preparedStatement.setString(2, realmId)
                preparedStatement.executeUpdate()
            }
    }

    fun removeForAccountId(conn: Connection, realmId: String, accountId: String) {
        getAll(conn, realmId).filter {
            if (it.actionAccountId == accountId) return@filter true

            if (it.action is AccountAction) {
                it.action.mainAccountId == accountId || it.action.additionalSplits.entries.any {
                    it.key == accountId
                }
            } else {
                false
            }
        }.forEach { remove(conn, it.id, realmId) }
    }
}

data class UnbookedBankTransactionMatcher(
    val id: String,
    val realmId: String,
    val name: String,
    val filter: UnbookedTransactionMatcherFilter,
    val action: UnbookedTransactionMatcherAction,
    val actionAccountId: String,
    val actionMemo: String?,
    val lastUsed: Instant,
)

@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS)
sealed class UnbookedTransactionMatcherFilter {
    abstract fun matches(t: UnbookedTransaction): Boolean
}

data class ContainsFilter(val value: String) : UnbookedTransactionMatcherFilter() {
    override fun matches(t: UnbookedTransaction): Boolean = t.memo?.lowercase()?.contains(value.lowercase()) ?: false
}

data class StartsWith(val value: String) : UnbookedTransactionMatcherFilter() {
    override fun matches(t: UnbookedTransaction): Boolean = t.memo?.lowercase()?.startsWith(value.lowercase()) ?: false
}

data class EndsWith(val value: String) : UnbookedTransactionMatcherFilter() {
    override fun matches(t: UnbookedTransaction): Boolean = t.memo?.lowercase()?.endsWith(value.lowercase()) ?: false
}

data class AmountBetween(val from: Long, val toExcluding: Long) : UnbookedTransactionMatcherFilter() {
    override fun matches(t: UnbookedTransaction): Boolean = t.amountInCents in from..<toExcluding
}

data class OrFilter(val filters: List<UnbookedTransactionMatcherFilter>) : UnbookedTransactionMatcherFilter() {
    override fun matches(t: UnbookedTransaction): Boolean = filters.any { it.matches(t) }
}

data class AndFilters(val filters: List<UnbookedTransactionMatcherFilter>) : UnbookedTransactionMatcherFilter() {
    override fun matches(t: UnbookedTransaction): Boolean = filters.all { it.matches(t) }

}

@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS)
sealed class UnbookedTransactionMatcherAction

object TransferAction : UnbookedTransactionMatcherAction()

enum class AccountActionType {
    accountsPayable,
    accountsReceivable,
}

data class AccountAction(
    val type: AccountActionType,
    val mainAccountId: String,
    val additionalSplits: Map<String, Long>,
) : UnbookedTransactionMatcherAction()

