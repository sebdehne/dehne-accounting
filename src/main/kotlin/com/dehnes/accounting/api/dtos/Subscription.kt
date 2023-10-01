package com.dehnes.accounting.api.dtos

import com.dehnes.accounting.database.ChangeLogEventType
import com.dehnes.accounting.database.ChangeLogEventType.*


data class Subscribe(
    val subscriptionId: String,
    val readRequest: ReadRequest,
)

data class Unsubscribe(
    val subscriptionId: String
)

data class Notify(
    val subscriptionId: String,
    val readResponse: ReadResponse,
)

enum class ReadRequestType(
    vararg listensOn: ChangeLogEventType,
) {
    userInfo(userUpdated),
    getLedgers(legderUpdated, legderRemoved, legderAdded, userLedgerAccessChanged),
    getBankAccounts(bankAdded, bankUpdated, bankRemoved, bankAccountAdded, bankAccountUpdated, bankAccountRemoved),

    ledgerRapport(bookingAdded, bookingRemoved),
    getBankTransactions(bankTransactionAdded, bankTransactionRemoveLast),
    getBankTransaction(bankTransactionAdded, bankTransactionRemoveLast),

    allCategories(categoryAdded, categoryUpdated, categoryRemoved),
    ;

    val events = listensOn.toList()
}

data class ReadRequest(
    val type: ReadRequestType,
    val ledgerId: String? = null,
    val ledgerRapportRequest: LedgerRapportRequest? = null,
    val bankTransactionsRequest: BankTransactionsRequest? = null,
    val bankTransactionRequest: BankTransactionRequest? = null,
)

data class ReadResponse(
    val ledgers: List<LedgerView>? = null,
    val userView: UserView? = null,
    val bankAccounts: List<BankAccountView>? = null,
    val ledgerRapport: List<LedgerRapportNode>? = null,
    val bankTransactions: List<BankAccountTransactionView>? = null,
    val bankTransaction: BankAccountTransactionView? = null,
    val categories: List<CategoryView>? = null,
)