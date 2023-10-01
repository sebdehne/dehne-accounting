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
    getBankTransactions(bankTransactionAdded, bankTransactionRemoveLast)
    ;

    val events = listensOn.toList()
}

data class ReadRequest(
    val type: ReadRequestType,
    val ledgerId: String? = null,
    val ledgerRapportRequest: LedgerRapportRequest? = null,
    val bankTransactionsRequest: BankTransactionsRequest? = null,
)

data class ReadResponse(
    val ledgers: List<LedgerView>? = null,
    val userView: UserView? = null,
    val bankAccounts: List<BankAccountView>? = null,
    val ledgerRapport: List<LedgerRapportNode>? = null,
    val bankTransactions: List<BankAccountTransactionView>? = null,
)