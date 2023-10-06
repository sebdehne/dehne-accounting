package com.dehnes.accounting.database

import com.dehnes.accounting.api.dtos.TransactionMatcher
import com.dehnes.accounting.api.dtos.TransactionMatcherActionType
import com.dehnes.accounting.api.dtos.UserState
import com.dehnes.accounting.domain.InformationElement
import com.dehnes.accounting.utils.SqlUtils
import com.dehnes.accounting.utils.toLong
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.sql.Connection
import java.sql.Date
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate

class Repository(
    private val changelog: Changelog,
    private val objectMapper: ObjectMapper,
) {

    /*
     * Bank
     */

    fun getAllBanks(connection: Connection): List<BankDto> =
        connection.prepareStatement("SELECT * FROM bank").use { preparedStatement ->
            preparedStatement.executeQuery().use { rs ->
                val l = mutableListOf<BankDto>()
                while (rs.next()) {
                    l.add(
                        BankDto(
                            rs.getString("id"),
                            rs.getString("name"),
                            rs.getString("description"),
                            rs.getString("transaction_import_function"),
                        )
                    )
                }
                l
            }
        }

    fun addOrReplaceBank(connection: Connection, userId: String, bank: BankDto) {
        val updated =
            connection.prepareStatement("UPDATE bank SET name=?, description=?, transaction_import_function=? WHERE id=?")
                .use { preparedStatement ->
                    preparedStatement.setString(1, bank.name)
                    preparedStatement.setString(2, bank.description)
                    preparedStatement.setString(3, bank.transactionImportFunction)
                    preparedStatement.setString(4, bank.id)
                    preparedStatement.executeUpdate() == 1
                }

        if (updated) {
            changelog.add(connection, userId, ChangeLogEventType.bankUpdated, bank)
        } else {
            connection.prepareStatement("INSERT INTO bank (id, name, description, transaction_import_function) VALUES (?,?,?,?)")
                .use { preparedStatement ->
                    preparedStatement.setString(1, bank.id)
                    preparedStatement.setString(2, bank.name)
                    preparedStatement.setString(3, bank.description)
                    preparedStatement.setString(4, bank.transactionImportFunction)
                    check(preparedStatement.executeUpdate() == 1) { "Could not insert after failed update" }
                }
            changelog.add(connection, userId, ChangeLogEventType.bankAdded, bank)
        }
    }

    fun removeBank(connection: Connection, userId: String, bankId: String) {
        val deleted = connection.prepareStatement("DELETE FROM bank WHERE id=?").use { preparedStatement ->
            preparedStatement.setString(1, bankId)
            preparedStatement.executeUpdate() == 0
        }
        if (deleted) {
            changelog.add(connection, userId, ChangeLogEventType.bankRemoved, mapOf("id" to bankId))
        }
    }

    /*
     * User
     */

    fun getAllUsers(connection: Connection): List<UserDto> =
        connection.prepareStatement("SELECT * FROM user").use { preparedStatement ->
            preparedStatement.executeQuery().use { rs ->
                val l = mutableListOf<UserDto>()
                while (rs.next()) {
                    l.add(
                        UserDto(
                            rs.getString("id"),
                            rs.getString("name"),
                            rs.getString("description"),
                            rs.getString("user_email"),
                            rs.getLong("active") > 0,
                            rs.getLong("is_admin") > 0,
                        )
                    )
                }
                l
            }
        }

    fun addOrReplaceUser(connection: Connection, userId: String, user: UserDto) {
        val updated =
            connection.prepareStatement("UPDATE user SET name=?, description=?, user_email=?,active=?,is_admin=? WHERE id=?")
                .use { preparedStatement ->
                    preparedStatement.setString(1, user.name)
                    preparedStatement.setString(2, user.description)
                    preparedStatement.setString(3, user.userEmail)
                    preparedStatement.setLong(4, user.active.toLong())
                    preparedStatement.setLong(5, user.isAdmin.toLong())
                    preparedStatement.setString(6, user.id)
                    preparedStatement.executeUpdate() == 1
                }

        if (updated) {
            changelog.add(connection, userId, ChangeLogEventType.userUpdated, user)
        } else {
            connection.prepareStatement("INSERT INTO user (id, name, description, user_email, active,is_admin) VALUES (?,?,?,?,?,?)")
                .use { preparedStatement ->
                    preparedStatement.setString(1, user.id)
                    preparedStatement.setString(2, user.name)
                    preparedStatement.setString(3, user.description)
                    preparedStatement.setString(4, user.userEmail)
                    preparedStatement.setLong(5, user.active.toLong())
                    preparedStatement.setLong(6, user.isAdmin.toLong())
                    check(preparedStatement.executeUpdate() == 1) { "Could not insert after failed update" }
                }
            changelog.add(connection, userId, ChangeLogEventType.userAdded, user)
        }
    }

    /*
     * Ledger
     */

    fun getAllLedger(connection: Connection): List<LedgerDto> =
        connection.prepareStatement("SELECT * FROM ledger").use { preparedStatement ->
            preparedStatement.executeQuery().use { rs ->
                val l = mutableListOf<LedgerDto>()
                while (rs.next()) {
                    l.add(toLedger(rs))
                }
                l
            }
        }

    fun getLedger(connection: Connection, ledgerId: String): LedgerDto =
        connection.prepareStatement("SELECT * FROM ledger WHERE id = ?").use { preparedStatement ->
            preparedStatement.setString(1, ledgerId)
            preparedStatement.executeQuery().use { rs ->
                check(rs.next())
                toLedger(rs)
            }
        }

    private fun toLedger(rs: ResultSet) = LedgerDto(
        id = rs.getString("id"),
        name = rs.getString("name"),
        description = rs.getString("description"),
        configuration = objectMapper.readValue(rs.getString("configuration_json")),
    )

    fun addOrReplaceLedger(connection: Connection, userId: String, ledger: LedgerDto) {
        val updated =
            connection.prepareStatement(
                """
                UPDATE ledger SET 
                    name=?, 
                    description=?, 
                    configuration_json=? 
                WHERE id=?
            """.trimIndent()
            )
                .use { preparedStatement ->
                    preparedStatement.setString(1, ledger.name)
                    preparedStatement.setString(2, ledger.description)
                    preparedStatement.setString(3, objectMapper.writeValueAsString(ledger.configuration))
                    preparedStatement.setString(4, ledger.id)
                    preparedStatement.executeUpdate() == 1
                }

        if (updated) {
            changelog.add(connection, userId, ChangeLogEventType.legderUpdated, ledger)
        } else {
            connection.prepareStatement(
                """
                INSERT INTO ledger (
                    id, 
                    name, 
                    description, 
                    configuration_json
                ) VALUES (?,?,?,?)
            """.trimIndent()
            ).use { preparedStatement ->
                preparedStatement.setString(1, ledger.id)
                preparedStatement.setString(2, ledger.name)
                preparedStatement.setString(3, ledger.description)
                preparedStatement.setString(4, objectMapper.writeValueAsString(ledger.configuration))
                check(preparedStatement.executeUpdate() == 1) { "Could not insert after failed update" }
            }
            changelog.add(connection, userId, ChangeLogEventType.legderAdded, ledger)
        }
    }

    fun removeLedger(connection: Connection, userId: String, ledgerId: String) {
        val deleted = connection.prepareStatement("DELETE FROM ledger WHERE id=?").use { preparedStatement ->
            preparedStatement.setString(1, ledgerId)
            preparedStatement.executeUpdate() == 0
        }
        if (deleted) {
            changelog.add(connection, userId, ChangeLogEventType.legderRemoved, mapOf("id" to ledgerId))
        }
    }

    /*
     * Bank account
     */
    fun getAllBankAccountsForLedger(connection: Connection, ledgerId: String): List<BankAccountDto> =
        connection.prepareStatement("SELECT * FROM bank_account WHERE ledger_id=?").use { preparedStatement ->
            preparedStatement.setString(1, ledgerId)
            preparedStatement.executeQuery().use { rs ->
                val l = mutableListOf<BankAccountDto>()
                while (rs.next()) {
                    l.add(
                        toBankAccount(rs)
                    )
                }
                l
            }
        }

    fun getBankAccount(connection: Connection, ledgerId: String, bankAccountId: String): BankAccountDto? =
        connection.prepareStatement("SELECT * FROM bank_account WHERE id=? and ledger_id = ?")
            .use { preparedStatement ->
                preparedStatement.setString(1, bankAccountId)
                preparedStatement.setString(2, ledgerId)
                preparedStatement.executeQuery().use { rs ->
                    if (rs.next()) {
                        toBankAccount(rs)
                    } else null
                }
            }

    private fun toBankAccount(rs: ResultSet) = BankAccountDto(
        rs.getString("id"),
        rs.getString("name"),
        rs.getString("description"),
        rs.getString("ledger_id"),
        rs.getString("bank_id"),
        rs.getString("account_number"),
        rs.getDate("open_date").toLocalDate(),
        rs.getDate("close_date")?.toLocalDate(),
        rs.getString("category_id"),
        rs.getLong("open_balance"),
        rs.getLong("transactions_counter"),
        rs.getLong("transactions_counter_unbooked"),
        rs.getLong("current_balance"),
    )

    private fun setTransactionsCountAndBalance(
        connection: Connection,
        bankAccountId: String,
        ledgerId: String,
        newCount: Long,
        newBalance: Long
    ) {
        connection.prepareStatement("UPDATE bank_account SET transactions_counter = ?, current_balance = ? WHERE id = ? and ledger_id = ?")
            .use { preparedStatement ->
                preparedStatement.setLong(1, newCount)
                preparedStatement.setLong(2, newBalance)
                preparedStatement.setString(3, bankAccountId)
                preparedStatement.setString(4, ledgerId)
                check(preparedStatement.executeUpdate() == 1)
            }
    }

    private fun setTransactionsCounterUnbooked(
        connection: Connection,
        userId: String,
        ledgerId: String,
        bankAccountId: String,
        newUnbooked: Long,
    ) {
        connection.prepareStatement("UPDATE bank_account SET transactions_counter_unbooked = ? WHERE id = ? and ledger_id = ?")
            .use { preparedStatement ->
                preparedStatement.setLong(1, newUnbooked)
                preparedStatement.setString(2, bankAccountId)
                preparedStatement.setString(3, ledgerId)
                check(preparedStatement.executeUpdate() == 1)
            }

        changelog.add(connection, userId, ChangeLogEventType.bankAccountUpdated, mapOf("id" to bankAccountId))
    }

    private fun decreaseUnbookedCounter(
        connection: Connection,
        ledgerId: String,
        userId: String,
        bankAccountId: String,
    ) {
        val b = getBankAccount(connection, ledgerId, bankAccountId) ?: error("No such bank account $bankAccountId")
        setTransactionsCounterUnbooked(
            connection = connection,
            userId = userId,
            ledgerId = ledgerId,
            bankAccountId = bankAccountId,
            newUnbooked = b.transactionsCounterUnbooked - 1,
        )
    }

    private fun increaseUnbookedCounter(
        connection: Connection,
        userId: String,
        ledgerId: String,
        bankAccountId: String,
    ) {
        val b = getBankAccount(connection, ledgerId, bankAccountId) ?: error("No such bank account $bankAccountId")
        setTransactionsCounterUnbooked(
            connection = connection,
            userId = userId,
            ledgerId = ledgerId,
            bankAccountId = bankAccountId,
            newUnbooked = b.transactionsCounterUnbooked + 1,
        )
    }

    fun updateBankAccount(connection: Connection, userId: String, bankAccount: BankAccountUpdateDto) {
        connection.prepareStatement("UPDATE bank_account SET name=?, description=?,account_number=?,open_date=?,close_date=? WHERE id=?")
            .use { preparedStatement ->
                preparedStatement.setString(1, bankAccount.name)
                preparedStatement.setString(2, bankAccount.description)
                preparedStatement.setString(3, bankAccount.accountNumber)
                preparedStatement.setDate(4, Date.valueOf(bankAccount.openDate))
                preparedStatement.setDate(5, bankAccount.closeDate?.let { Date.valueOf(it) })
                preparedStatement.setString(6, bankAccount.id)
                check(preparedStatement.executeUpdate() == 0) { "Could not update" }
            }

        changelog.add(connection, userId, ChangeLogEventType.bankAccountUpdated, bankAccount)
    }

    fun addBankAccount(connection: Connection, userId: String, bankAccount: BankAccountDto) {
        connection.prepareStatement(
            """
            INSERT INTO bank_account (
                id, 
                name, 
                description, 
                ledger_id, 
                bank_id, 
                account_number, 
                open_date, 
                close_date, 
                category_id, 
                open_balance,
                transactions_counter,
                transactions_counter_unbooked,
                current_balance
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
        """.trimIndent()
        )
            .use { preparedStatement ->
                preparedStatement.setString(1, bankAccount.id)
                preparedStatement.setString(2, bankAccount.name)
                preparedStatement.setString(3, bankAccount.description)
                preparedStatement.setString(4, bankAccount.ledgerId)
                preparedStatement.setString(5, bankAccount.bankId)
                preparedStatement.setString(6, bankAccount.accountNumber)
                preparedStatement.setDate(7, Date.valueOf(bankAccount.openDate))
                preparedStatement.setDate(8, bankAccount.closeDate?.let { Date.valueOf(it) })
                preparedStatement.setString(9, bankAccount.categoryId)
                preparedStatement.setLong(10, bankAccount.openBalance)
                preparedStatement.setLong(11, 0)
                preparedStatement.setLong(12, 0)
                preparedStatement.setLong(13, bankAccount.openBalance)
                check(preparedStatement.executeUpdate() == 1) { "Could not insert" }
            }

        changelog.add(connection, userId, ChangeLogEventType.bankAccountAdded, bankAccount)
    }

    fun removeBankAccount(connection: Connection, userId: String, bankAccountId: String) {
        val deleted = connection.prepareStatement("DELETE FROM bank_account WHERE id=?").use { preparedStatement ->
            preparedStatement.setString(1, bankAccountId)
            preparedStatement.executeUpdate() == 0
        }
        if (deleted) {
            changelog.add(connection, userId, ChangeLogEventType.bankAccountRemoved, mapOf("id" to bankAccountId))
        }
    }

    /*
     * User-ledger
     */
    fun getUserLedgers(connection: Connection): List<UserLedger> =
        connection.prepareStatement("SELECT * FROM user_ledger").use { preparedStatement ->
            preparedStatement.executeQuery().use { rs ->
                val l = mutableListOf<UserLedger>()
                while (rs.next()) {
                    l.add(
                        UserLedger(
                            rs.getString("user_id"),
                            rs.getString("ledger_id"),
                            AccessLevel.valueOf(rs.getString("access_level")),
                        )
                    )
                }
                l
            }
        }

    fun changeUserLedgerAccessLevel(connection: Connection, userId: String, userLedger: UserLedger) {

        if (userLedger.accessLevel == AccessLevel.none) {
            connection.prepareStatement("DELETE FROM user_ledger WHERE user_id=? AND ledger_id=?")
                .use { preparedStatement ->
                    preparedStatement.setString(1, userLedger.userId)
                    preparedStatement.setString(2, userLedger.ledgerId)
                    preparedStatement.executeUpdate()
                }
        } else {
            val updated =
                connection.prepareStatement("UPDATE user_ledger SET access_level=? WHERE ledger_id=? AND user_id=?")
                    .use { preparedStatement ->
                        preparedStatement.setString(1, userLedger.accessLevel.name)
                        preparedStatement.setString(2, userLedger.ledgerId)
                        preparedStatement.setString(3, userLedger.userId)
                        preparedStatement.executeUpdate() > 0
                    }

            if (!updated) {
                connection.prepareStatement("INSERT INTO user_ledger (user_id, ledger_id, access_level) VALUES (?,?,?)")
                    .use { preparedStatement ->
                        preparedStatement.setString(1, userLedger.userId)
                        preparedStatement.setString(2, userLedger.ledgerId)
                        preparedStatement.setString(3, userLedger.accessLevel.name)
                        check(preparedStatement.executeUpdate() > 0) { "Insert failed" }
                    }

            }
        }


        changelog.add(connection, userId, ChangeLogEventType.userLedgerAccessChanged, userLedger)
    }


    /*
     * Category
     */
    fun getAllCategories(connection: Connection, ledgerId: String): List<CategoryDto> =
        connection.prepareStatement("SELECT * FROM category where ledger_id = ?").use { preparedStatement ->
            preparedStatement.setString(1, ledgerId)
            preparedStatement.executeQuery().use { rs ->
                val l = mutableListOf<CategoryDto>()
                while (rs.next()) {
                    l.add(
                        CategoryDto(
                            rs.getString("id"),
                            rs.getString("name"),
                            rs.getString("description"),
                            rs.getString("parent_category_id"),
                            rs.getString("ledger_id"),
                        )
                    )
                }
                l
            }
        }

    fun addOrReplaceCategory(
        connection: Connection,
        userId: String,
        category: CategoryDto
    ) {

        check(category.name.isNotBlank())
        val updated =
            connection.prepareStatement(
                """
                UPDATE category SET 
                    name=?, 
                    description=?,
                    parent_category_id=? 
                WHERE id=? and ledger_id = ?
            """.trimIndent()
            )
                .use { preparedStatement ->
                    preparedStatement.setString(1, category.name)
                    preparedStatement.setString(2, category.description?.ifBlank { null })
                    preparedStatement.setString(3, category.parentCategoryId)
                    preparedStatement.setString(4, category.id)
                    preparedStatement.setString(5, category.ledgerId)
                    preparedStatement.executeUpdate() == 1
                }

        if (updated) {
            changelog.add(connection, userId, ChangeLogEventType.categoryUpdated, category)
        } else {
            connection.prepareStatement(
                """
                INSERT INTO category (
                    id, 
                    name, 
                    description,
                    parent_category_id,
                    ledger_id
                ) VALUES (?,?,?,?,?)
            """.trimIndent()
            )
                .use { preparedStatement ->
                    preparedStatement.setString(1, category.id)
                    preparedStatement.setString(2, category.name)
                    preparedStatement.setString(3, category.description)
                    preparedStatement.setString(4, category.parentCategoryId)
                    preparedStatement.setString(5, category.ledgerId)
                    check(preparedStatement.executeUpdate() == 1) { "Could not insert after failed update" }
                }
            changelog.add(connection, userId, ChangeLogEventType.categoryAdded, category)
        }
    }

    fun removeCategory(
        connection: Connection,
        userId: String,
        ledgerId: String,
        categoryId: String
    ) {
        connection.prepareStatement("DELETE FROM category WHERE id=? AND ledger_id = ?").use { preparedStatement ->
            preparedStatement.setString(1, categoryId)
            preparedStatement.setString(2, ledgerId)
            preparedStatement.executeUpdate()
        }
        changelog.add(connection, userId, ChangeLogEventType.categoryRemoved, mapOf("id" to categoryId))
    }

    /*
     * Transaction matchers
     */
    fun getAllMatchers(connection: Connection, ledgerId: String): List<TransactionMatcher> {
        return connection.prepareStatement("SELECT * FROM bank_transaction_matchers where ledger_id = ? order by last_used desc")
            .use { preparedStatement ->
                preparedStatement.setString(1, ledgerId)
                preparedStatement.executeQuery().use { rs ->
                    val l = mutableListOf<TransactionMatcher>()
                    while (rs.next()) {
                        l.add(
                            TransactionMatcher(
                                id = rs.getString("id"),
                                ledgerId = rs.getString("ledger_id"),
                                name = rs.getString("name"),
                                filters = objectMapper.readValue(rs.getString("filter_list")),
                                action = objectMapper.readValue(rs.getString("action")),
                                lastUsed = rs.getTimestamp("last_used").toInstant(),
                            )
                        )
                    }
                    l
                }
            }
    }

    fun matchUsed(connection: Connection, matcherId: String) {
        connection.prepareStatement("UPDATE bank_transaction_matchers SET last_used = ? WHERE id = ?")
            .use { preparedStatement ->
                preparedStatement.setTimestamp(1, Timestamp.from(Instant.now()))
                preparedStatement.setString(2, matcherId)
                check(preparedStatement.executeUpdate() == 1)
            }
    }

    fun addOrReplaceMatcher(connection: Connection, userId: String, matcher: TransactionMatcher) {
        check(matcher.filters.isNotEmpty()) { "Matcher has no filter rules" }
        when (matcher.action.type) {
            TransactionMatcherActionType.bankTransfer -> check(matcher.action.transferCategoryId != null) { "Transfer matcher must have a category" }
            TransactionMatcherActionType.paymentOrIncome -> check(matcher.action.paymentOrIncomeConfig != null) { "Payment/income matcher must have valid config" }
        }

        val updated =
            connection.prepareStatement("UPDATE bank_transaction_matchers SET name=?,filter_list=?, action=? WHERE id=?")
                .use { preparedStatement ->
                    preparedStatement.setString(1, matcher.name)
                    preparedStatement.setString(2, objectMapper.writeValueAsString(matcher.filters))
                    preparedStatement.setString(3, objectMapper.writeValueAsString(matcher.action))
                    preparedStatement.setString(4, matcher.id)
                    preparedStatement.executeUpdate() == 1
                }

        if (updated) {
            changelog.add(connection, userId, ChangeLogEventType.matcherUpdated, matcher)
        } else {
            connection.prepareStatement("INSERT INTO bank_transaction_matchers (id, ledger_id, name,filter_list, action, last_used) VALUES (?,?,?,?,?,?)")
                .use { preparedStatement ->
                    preparedStatement.setString(1, matcher.id)
                    preparedStatement.setString(2, matcher.ledgerId)
                    preparedStatement.setString(3, matcher.name)
                    preparedStatement.setString(4, objectMapper.writeValueAsString(matcher.filters))
                    preparedStatement.setString(5, objectMapper.writeValueAsString(matcher.action))
                    preparedStatement.setTimestamp(6, Timestamp.from(Instant.now()))
                    check(preparedStatement.executeUpdate() == 1) { "Could not insert after failed update" }
                }
            changelog.add(connection, userId, ChangeLogEventType.matcherAdded, matcher)
        }
    }

    fun removeMatcher(connection: Connection, userId: String, matcherId: String) {
        connection.prepareStatement("DELETE FROM bank_transaction_matchers where id = ?").use { preparedStatement ->
            preparedStatement.setString(1, matcherId)
            preparedStatement.executeUpdate()
        }
        changelog.add(connection, userId, ChangeLogEventType.matcherRemoved, mapOf("id" to matcherId))
    }

    /*
     * Bank transactions
     */
    fun getBankTransactions(
        connection: Connection,
        bankAccountId: String,
        limit: Int,
        vararg filters: BankAccountTransactionsFilter?,
    ): List<BankTransaction> {
        check(limit >= 0)
        val params = mutableListOf<Any>()
        params.add(bankAccountId)

        val filterData = filters.filterNotNull().map { it.whereAndParams() }
        val wheres = filterData.map { it.first }.joinToString(" AND ") { "($it)" }
        filterData.map { it.second }.forEach { params.addAll(it) }

        return connection.prepareStatement(
            """
            SELECT 
                * 
            FROM 
                bank_transaction 
            where 
                bank_account_id = ? and $wheres
            ORDER BY id DESC
            LIMIT $limit 
        """.trimIndent()
        )
            .use { preparedStatement ->
                SqlUtils.setSqlParams(preparedStatement, params)

                preparedStatement.executeQuery().use { rs ->
                    val l = mutableListOf<BankTransaction>()
                    while (rs.next()) {
                        l.add(toBankTransaction(rs))
                    }
                    l
                }
            }
    }

    fun getBankTransaction(
        connection: Connection,
        bankAccountId: String,
        id: Long,
        ledgerId: String,
    ): BankTransaction =
        connection.prepareStatement("SELECT * FROM bank_transaction where bank_account_id = ? and id = ? and ledger_id = ?")
            .use { preparedStatement ->
                preparedStatement.setString(1, bankAccountId)
                preparedStatement.setLong(2, id)
                preparedStatement.setString(3, ledgerId)
                preparedStatement.executeQuery().use { rs ->
                    check(rs.next())
                    toBankTransaction(rs)
                }
            }

    fun getTotalUnmatched(conn: Connection, ledgerId: String, bankAccountId: String): Long {
        return conn.prepareStatement("""
            SELECT 
                COUNT(*) count 
            FROM 
                bank_transaction 
            where 
                ledger_id = ? 
                AND bank_account_id = ? 
                AND matched_booking_id is null
        """.trimIndent()).use { preparedStatement ->
            preparedStatement.setString(1, ledgerId)
            preparedStatement.setString(2, bankAccountId)
            preparedStatement.executeQuery().use { rs ->
                check(rs.next())
                rs.getLong("count")
            }
        }
    }


    private fun toBankTransaction(rs: ResultSet) = BankTransaction(
        rs.getLong("id"),
        rs.getString("description"),
        rs.getString("ledger_id"),
        rs.getString("bank_id"),
        rs.getString("bank_account_id"),
        rs.getTimestamp("datetime").toInstant(),
        rs.getLong("amount"),
        rs.getLong("balance"),
        rs.getString("matched_ledger_id"),
        rs.getLong("matched_booking_id").let {
            if (rs.wasNull()) null else it
        },
    )

    fun removeLastBankTransaction(
        connection: Connection,
        userId: String,
        ledgerId: String,
        bankAccountId: String
    ) {
        val bankAccount =
            getBankAccount(connection, ledgerId, bankAccountId) ?: error("Unknown bank account $bankAccountId")
        val lastTransaction = getBankTransaction(
            connection = connection,
            bankAccountId = bankAccountId,
            id = bankAccount.transactionsCounter,
            ledgerId = ledgerId
        )
        check(lastTransaction.matchedBookingId == null) { "Cannot a matched transaction, remove booking reference first" }

        connection.prepareStatement("DELETE FROM bank_transaction WHERE bank_account_id = ? and id = ? and ledger_id = ?")
            .use { preparedStatement ->
                preparedStatement.setString(1, bankAccount.id)
                preparedStatement.setLong(2, bankAccount.transactionsCounter)
                preparedStatement.setString(3, ledgerId)
                check(preparedStatement.executeUpdate() == 1) { "Could not remove last bank transkaction" }
            }

        setTransactionsCountAndBalance(
            connection = connection,
            bankAccountId = bankAccountId,
            newCount = bankAccount.transactionsCounter - 1,
            ledgerId = ledgerId,
            newBalance = lastTransaction.balance - lastTransaction.amount
        )
        decreaseUnbookedCounter(
            connection = connection,
            ledgerId = ledgerId,
            userId = userId,
            bankAccountId = bankAccountId
        )

        changelog.add(
            connection,
            userId,
            ChangeLogEventType.bankTransactionRemoveLast,
            mapOf("bankAccountId" to bankAccountId)
        )
    }


    fun addBankTransaction(connection: Connection, userId: String, bankTransaction: BankTransactionAdd): Long {
        val bankAccount = getBankAccount(connection, bankTransaction.ledgerId, bankTransaction.bankAccountId)
            ?: error("Unknown bank account ${bankTransaction.bankAccountId}")
        val nextId = bankAccount.transactionsCounter + 1
        val newBalance = bankAccount.currentBalance + bankTransaction.amount

        connection.prepareStatement(
            """
            INSERT INTO bank_transaction (
                id, 
                description, 
                ledger_id, 
                bank_id, 
                bank_account_id, 
                datetime, 
                amount, 
                balance, 
                matched_ledger_id,
                matched_booking_id
             ) VALUES (?,?,?,?,?,?,?,?,?,?)
        """.trimIndent()
        ).use { preparedStatement ->
            preparedStatement.setLong(1, nextId)
            preparedStatement.setString(2, bankTransaction.description)
            preparedStatement.setString(3, bankTransaction.ledgerId)
            preparedStatement.setString(4, bankTransaction.bankId)
            preparedStatement.setString(5, bankTransaction.bankAccountId)
            preparedStatement.setTimestamp(6, Timestamp.from(bankTransaction.datetime))
            preparedStatement.setLong(7, bankTransaction.amount)
            preparedStatement.setLong(8, newBalance)
            preparedStatement.setString(9, null)
            preparedStatement.setObject(10, null)

            check(preparedStatement.executeUpdate() == 1)
        }

        setTransactionsCountAndBalance(
            connection = connection,
            bankAccountId = bankAccount.id,
            newCount = nextId,
            newBalance = newBalance,
            ledgerId = bankTransaction.ledgerId,
        )
        increaseUnbookedCounter(connection, userId, bankTransaction.ledgerId, bankAccount.id)

        changelog.add(connection, userId, ChangeLogEventType.bankTransactionAdded, bankTransaction)

        return nextId
    }

    fun matchBankTransaction(
        connection: Connection,
        userId: String,
        bankAccountId: String,
        transactionId: Long,
        ledgerId: String,
        bookingId: Long,
    ) {
        connection.prepareStatement(
            """
            UPDATE bank_transaction set 
                matched_ledger_id =?, 
                matched_booking_id = ? 
            WHERE 
                bank_account_id = ? AND id = ? and ledger_id = ?
        """.trimIndent()
        ).use { preparedStatement ->
            preparedStatement.setString(1, ledgerId)
            preparedStatement.setLong(2, bookingId)
            preparedStatement.setString(3, bankAccountId)
            preparedStatement.setLong(4, transactionId)
            preparedStatement.setString(5, ledgerId)
            check(preparedStatement.executeUpdate() == 1)
        }

        decreaseUnbookedCounter(
            connection = connection,
            ledgerId = ledgerId,
            userId = userId,
            bankAccountId = bankAccountId
        )
    }

    fun findMatchedTransactions(
        connection: Connection,
        ledgerId: String,
        bookingId: Long,
    ): List<BankTransaction> =
        connection.prepareStatement("SELECT * FROM bank_transaction where matched_ledger_id = ? AND matched_booking_id = ?")
            .use { preparedStatement ->
                preparedStatement.setString(1, ledgerId)
                preparedStatement.setLong(2, bookingId)
                preparedStatement.executeQuery().use { rs ->
                    val l = mutableListOf<BankTransaction>()
                    while (rs.next()) {
                        l.add(toBankTransaction(rs))
                    }
                    l
                }
            }

    private fun unmatchBankTransaction(
        connection: Connection,
        userId: String,
        bankTransaction: BankTransaction,
        ledgerId: String,
    ) {
        connection.prepareStatement(
            """
            UPDATE bank_transaction set 
                matched_ledger_id = null, 
                matched_booking_id = null
            WHERE 
                bank_account_id = ? AND id = ? AND ledger_id = ?
        """.trimIndent()
        ).use { preparedStatement ->
            preparedStatement.setString(1, bankTransaction.bankAccountId)
            preparedStatement.setLong(2, bankTransaction.id)
            preparedStatement.setString(3, ledgerId)
            check(preparedStatement.executeUpdate() == 1)
        }

        increaseUnbookedCounter(connection, userId, ledgerId, bankTransaction.bankAccountId)
    }

    /*
     * Bookings
     */
    fun addBooking(connection: Connection, userId: String, booking: BookingView): Long {
        check(booking.records.isNotEmpty()) { "Cannot add booking without records" }
        check(booking.records.sumOf { it.amount } == 0L) { "Records to not accumulate to zero" }

        val ledger = getLedger(connection, booking.ledgerId)
        val nextId = ledger.configuration.bookingsCounter + 1

        connection.prepareStatement(
            """
            INSERT INTO booking (
                ledger_id, 
                id, 
                description, 
                datetime
            ) VALUES (?,?,?,?)
        """.trimIndent()
        ).use { preparedStatement ->
            preparedStatement.setString(1, booking.ledgerId)
            preparedStatement.setLong(2, nextId)
            preparedStatement.setString(3, booking.description)
            preparedStatement.setTimestamp(4, Timestamp.from(booking.datetime))
            check(preparedStatement.executeUpdate() == 1) { "Could not insert booking" }
        }

        booking.records.forEachIndexed { index, record ->
            connection.prepareStatement(
                """
                INSERT INTO booking_record (
                    ledger_id, 
                    booking_id, 
                    id, 
                    description, 
                    category_id, 
                    amount
                ) VALUES (?,?,?,?,?,?)
            """.trimIndent()
            ).use { preparedStatement ->
                preparedStatement.setString(1, booking.ledgerId)
                preparedStatement.setLong(2, nextId)
                preparedStatement.setInt(3, index)
                preparedStatement.setString(4, record.description)
                preparedStatement.setString(5, record.categoryId)
                preparedStatement.setLong(6, record.amount)
                check(preparedStatement.executeUpdate() == 1)
            }
        }

        changelog.add(connection, userId, ChangeLogEventType.bookingAdded, booking)

        addOrReplaceLedger(connection, userId, ledger.addBookingsCounter(1))

        return nextId
    }

    fun editBooking(
        connection: Connection,
        userId: String,
        booking: BookingView
    ) {
        check(booking.records.isNotEmpty()) { "Cannot add booking without records" }
        check(booking.records.sumOf { it.amount } == 0L) { "Records to not accumulate to zero" }

        // delete records
        connection.prepareStatement(
            """
            DELETE FROM booking_record where ledger_id = ? and booking_id = ?
        """.trimIndent()
        ).use { preparedStatement ->
            preparedStatement.setString(1, booking.ledgerId)
            preparedStatement.setLong(2, booking.id)
            preparedStatement.executeUpdate()
        }

        // re-add records
        booking.records.forEachIndexed { index, r ->
            connection.prepareStatement(
                """
                INSERT INTO booking_record (
                    ledger_id, 
                    booking_id, 
                    id, 
                    description, 
                    category_id, 
                    amount
                ) VALUES (?,?,?,?,?,?)
            """.trimIndent()
            ).use { preparedStatement ->
                preparedStatement.setString(1, booking.ledgerId)
                preparedStatement.setLong(2, booking.id)
                preparedStatement.setLong(3, index.toLong())
                preparedStatement.setString(4, r.description)
                preparedStatement.setString(5, r.categoryId)
                preparedStatement.setLong(6, r.amount)
                check(preparedStatement.executeUpdate() == 1)
            }
        }

        // update description on booking
        connection.prepareStatement(
            """
            UPDATE booking set description = ?, datetime = ? WHERE ledger_id = ? AND id = ?
        """.trimIndent()
        ).use { preparedStatement ->
            preparedStatement.setString(1, booking.description)
            preparedStatement.setTimestamp(2, Timestamp.from(booking.datetime))
            preparedStatement.setString(3, booking.ledgerId)
            preparedStatement.setLong(4, booking.id)
            check(preparedStatement.executeUpdate() == 1)
        }

        changelog.add(
            connection,
            userId,
            ChangeLogEventType.bookingChanged,
            mapOf(
                "ledgerId" to booking.ledgerId,
                "bookingId" to booking.id,
            )
        )
    }

    fun getBookings(
        connection: Connection,
        ledgerId: String,
        limit: Int,
        vararg filters: BookingsFilter?,
    ): List<BookingView> {
        check(limit >= 0)

        val params = mutableListOf<Any>()
        params.add(ledgerId)

        val filterData = filters.filterNotNull().map { it.whereAndParams() }
        val wheres = filterData.map { it.first }.joinToString(" AND ") { "($it)" }
        filterData.map { it.second }.forEach { params.addAll(it) }

        val records = connection.prepareStatement(
            """
            SELECT 
                b.id, 
                b.description, 
                b.datetime, 
                br.id brId, 
                br.description brDescription, 
                br.category_id, 
                br.amount 
            FROM 
                booking b
            LEFT JOIN 
                booking_record br on b.id = br.booking_id AND b.ledger_id = br.ledger_id
            WHERE
                b.ledger_id = ? AND $wheres
            ORDER BY b.datetime desc, b.id desc , brId
            LIMIT $limit
        """.trimIndent()
        ).use { preparedStatement ->
            SqlUtils.setSqlParams(preparedStatement, params)

            preparedStatement.executeQuery().use { rs ->
                val l = mutableListOf<BookingBookingRecord>()
                while (rs.next()) {
                    l.add(
                        BookingBookingRecord(
                            ledgerId,
                            rs.getLong("id"),
                            rs.getString("description"),
                            rs.getTimestamp("datetime").toInstant(),
                            rs.getLong("brId"),
                            rs.getString("brDescription"),
                            rs.getString("category_id"),
                            rs.getLong("amount")
                        )
                    )
                }
                l
            }
        }

        return records.groupBy { it.bookingId }.map { (bId, bRecords) ->
            val bookingRecord = records.first { it.bookingId == bId }
            val bookingRecordViews = bRecords.map { r ->
                BookingRecordView(
                    ledgerId,
                    bId,
                    r.bookingRecordId,
                    r.bookingRecordDescription,
                    r.categoryId,
                    r.amountInCents,
                )
            }
            BookingView(
                ledgerId,
                bId,
                bookingRecord.bookingDescription,
                bookingRecord.datetime,
                bookingRecordViews,
            )
        }
    }

    fun removeBooking(
        connection: Connection,
        userId: String,
        ledgerId: String,
        bookingId: Long
    ) {
        // clear possible matches
        findMatchedTransactions(connection, ledgerId, bookingId).forEach { bankTransaction ->
            unmatchBankTransaction(
                connection,
                userId,
                bankTransaction,
                ledgerId
            )
        }

        // delete all records
        connection.prepareStatement("DELETE FROM booking_record WHERE ledger_id = ? AND booking_id = ?")
            .use { preparedStatement ->
                preparedStatement.setString(1, ledgerId)
                preparedStatement.setLong(2, bookingId)
                preparedStatement.executeUpdate()
            }

        // delete booking
        connection.prepareStatement("DELETE from booking where ledger_id = ? AND id = ?").use { preparedStatement ->
            preparedStatement.setString(1, ledgerId)
            preparedStatement.setLong(2, bookingId)
            preparedStatement.executeUpdate()
        }

        changelog.add(
            connection,
            userId,
            ChangeLogEventType.bookingRemoved,
            mapOf(
                "ledgerId" to ledgerId,
                "bookingId" to bookingId
            )
        )
    }

    fun changeCategory(
        connection: Connection,
        userId: String,
        ledgerId: String,
        bookingId: Long,
        bookingRecordId: Long,
        sourceCategoryId: String,
        destinationCategoryId: String,
    ) {
        val updated = connection.prepareStatement(
            """
            UPDATE booking_record SET 
                category_id = ? 
            WHERE 
                ledger_id = ? 
                AND booking_id = ? 
                AND id = ?
                AND category_id = ?
        """.trimIndent()
        ).use { preparedStatement ->
            preparedStatement.setString(1, destinationCategoryId)
            preparedStatement.setString(2, ledgerId)
            preparedStatement.setLong(3, bookingId)
            preparedStatement.setLong(4, bookingRecordId)
            preparedStatement.setString(5, sourceCategoryId)
            preparedStatement.executeUpdate() > 0
        }
        if (updated) {
            changelog.add(
                connection, userId, ChangeLogEventType.bookingChanged, mapOf(
                    "ledgerId" to ledgerId,
                    "bookingId" to bookingId,
                    "id" to bookingRecordId,
                )
            )
        }
    }

    /*
     * User state
     */
    fun getUserState(connection: Connection, userId: String) = connection
        .prepareStatement("SELECT * FROM user_state where user_id = ?").use { preparedStatement ->
            preparedStatement.setString(1, userId)
            preparedStatement.executeQuery().use { rs ->
                if (rs.next()) {
                    UserState(
                        objectMapper.readValue(rs.getString("frontend_state"))
                    )
                } else UserState(emptyMap())
            }
        }

    fun setUserState(connection: Connection, userId: String, userState: UserState) {
        val updated = connection.prepareStatement("UPDATE user_state SET frontend_state = ? WHERE user_id = ?")
            .use { preparedStatement ->
                preparedStatement.setString(1, objectMapper.writeValueAsString(userState.frontendState))
                preparedStatement.setString(2, userId)
                preparedStatement.executeUpdate() > 0
            }
        if (!updated) {
            connection.prepareStatement("INSERT INTO user_state (user_id, frontend_state) VALUES (?,?)")
                .use { preparedStatement ->
                    preparedStatement.setString(1, userId)
                    preparedStatement.setString(2, objectMapper.writeValueAsString(userState.frontendState))
                    check(preparedStatement.executeUpdate() == 1) { "Could not insert after failed update" }
                }
        }
        changelog.add(
            connection,
            userId,
            ChangeLogEventType.userStateUpdated,
            mapOf("userId" to userId)
        )
    }

}

enum class AccessLevel {
    admin,
    legderOwner,
    legderReadWrite,
    legderRead,
    none,
    ;

    fun hasAccess(req: AccessRequest) = when (req) {
        AccessRequest.admin -> this == admin
        AccessRequest.owner -> this in listOf(admin, legderOwner)
        AccessRequest.write -> this in listOf(admin, legderOwner, legderReadWrite)
        AccessRequest.read -> this in listOf(admin, legderOwner, legderReadWrite, legderRead)
    }
}

enum class AccessRequest {
    read,
    write,
    owner,
    admin
}

data class BankDto(
    override val id: String,
    override val name: String,
    override val description: String?,
    val transactionImportFunction: String?,
) : InformationElement()

data class UserDto(
    override val id: String,
    override val name: String,
    override val description: String?,
    val userEmail: String,
    val active: Boolean,
    val isAdmin: Boolean,
) : InformationElement()

data class LedgerDto(
    override val id: String,
    override val name: String,
    override val description: String?,
    val configuration: LedgerConfiguration,
) : InformationElement() {
    fun addBookingsCounter(delta: Int) = copy(
        configuration = configuration.copy(
            bookingsCounter = configuration.bookingsCounter + delta
        )
    )
}

data class LedgerConfiguration(
    val currency: String,
    val bookingsCounter: Long = 0,
    val categoryIdsForExpensesAndIncome: List<String> = emptyList(),
    val categoryIdsForBankAccounts: List<String> = emptyList(),
    val categoryIdsForPayees: List<String> = emptyList(),
)

data class BankAccountDto(
    override val id: String,
    override val name: String,
    override val description: String?,
    val ledgerId: String,
    val bankId: String,
    val accountNumber: String,
    val openDate: LocalDate,
    val closeDate: LocalDate?,
    val categoryId: String,
    val openBalance: Long,
    val transactionsCounter: Long,
    val transactionsCounterUnbooked: Long,
    val currentBalance: Long,
) : InformationElement()

data class BankAccountUpdateDto(
    override val id: String,
    override val name: String,
    override val description: String?,
    val accountNumber: String,
    val openDate: LocalDate,
    val closeDate: LocalDate?
) : InformationElement()

data class UserLedger(
    val userId: String,
    val ledgerId: String,
    val accessLevel: AccessLevel,
)

data class CategoryDto(
    override val id: String,
    override val name: String,
    override val description: String?,
    val parentCategoryId: String?,
    val ledgerId: String,
) : InformationElement()


data class BankTransaction(
    val id: Long,
    val description: String?,
    val ledgerId: String,
    val bankId: String,
    val bankAccountId: String,
    val datetime: Instant,
    val amount: Long,
    val balance: Long,
    val matchedLedgerId: String?,
    val matchedBookingId: Long?,
)

data class BankTransactionAdd(
    val description: String?,
    val ledgerId: String,
    val bankId: String,
    val bankAccountId: String,
    val datetime: Instant,
    val amount: Long,
)


data class BookingRecordView(
    val ledgerId: String,
    val bookingId: Long,
    val id: Long,
    val description: String?,
    val categoryId: String,
    val amount: Long,
)

data class BookingView(
    val ledgerId: String,
    val id: Long,
    val description: String?,
    val datetime: Instant,
    val records: List<BookingRecordView>,
)

data class BookingBookingRecord(
    val ledgerId: String,
    val bookingId: Long,
    val bookingDescription: String?,
    val datetime: Instant,
    val bookingRecordId: Long,
    val bookingRecordDescription: String?,
    val categoryId: String,
    val amountInCents: Long,
)


interface QueryFilter {
    fun whereAndParams(): Pair<String, List<Any>>
}

interface BookingsFilter : QueryFilter
interface BankAccountTransactionsFilter : QueryFilter

class DateRangeFilter(
    private val from: Instant,
    private val toExclusive: Instant,
) : BookingsFilter {
    override fun whereAndParams(): Pair<String, List<Any>> {
        return "b.datetime >= ? AND b.datetime < ?" to listOf(
            from,
            toExclusive
        )
    }
}

class CategoryFilter(
    private val categoryIds: List<String>,
) : BookingsFilter {
    override fun whereAndParams(): Pair<String, List<Any>> =
        "br.category_id in (${categoryIds.joinToString(",") { "?" }})" to categoryIds
}

class SingleBookingFilter(
    private val bookingId: Long,
) : BookingsFilter {
    override fun whereAndParams(): Pair<String, List<Any>> =
        "b.id = ?" to listOf(bookingId)
}

class BankTxDateRangeFilter(
    private val from: Instant,
    private val toExclusive: Instant,
) : BankAccountTransactionsFilter {
    override fun whereAndParams(): Pair<String, List<Any>> {
        return "datetime >= ? AND datetime < ?" to listOf(
            from,
            toExclusive
        )
    }
}
