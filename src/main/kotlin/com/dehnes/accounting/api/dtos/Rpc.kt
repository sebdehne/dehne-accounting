package com.dehnes.accounting.api.dtos

import com.dehnes.accounting.bank.importers.DuplicationHandler
import com.dehnes.accounting.bank.importers.ImportResult


enum class RequestType {
    subscribe,
    unsubscribe,

    importBankTransactions,
}

data class RpcRequest(
    val type: RequestType,
    val subscribe: Subscribe?,
    val unsubscribe: Unsubscribe?,
    val importBankTransactionsRequest: ImportBankTransactionsRequest?,
)

data class RpcResponse(
    val subscriptionCreated: Boolean? = null,
    val subscriptionRemoved: Boolean? = null,

    val importBankTransactionsResult: ImportResult? = null,
)

data class ImportBankTransactionsRequest(
    val ledgerId: String,
    val bankAccountId: String,
    val filename: String,
    val dataBase64: String,
    val duplicationHandlerType: DuplicationHandlerType,
)

enum class DuplicationHandlerType(val duplicationHandler: DuplicationHandler) {
    sameDateAndAmount({ a, b -> a.datetime == b.datetime && a.amount == b.amountInCents }),
    sameDateAmountAndDescription({ a, b -> a.datetime == b.datetime && a.amount == b.amountInCents && a.description == b.description }),
}
