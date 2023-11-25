package com.dehnes.accounting.api.dtos

import com.dehnes.accounting.bank.importers.DuplicationHandler
import com.dehnes.accounting.bank.importers.ImportResult
import com.dehnes.accounting.database.AccountDto
import com.dehnes.accounting.database.BankAccount
import com.dehnes.accounting.database.Booking
import com.dehnes.accounting.database.UnbookedBankTransactionMatcher


enum class RequestType {
    subscribe,
    unsubscribe,

    importBankTransactions,

    setUserStateV2,

    deleteAllUnbookedTransactions,
    deleteUnbookedTransaction,
    addOrReplaceUnbookedTransactionMatcher,
    removeUnbookedTransactionMatcher,

    executeMatcherUnbookedTransactionMatcher,

    createOrUpdateBooking,
    deleteBooking,

    mergeAccount,
    createOrUpdateAccount,

    deleteBankAccount,
    createOrUpdateBankAccount,

    // admin commands
    addOrReplaceUser,
    deleteUser,
}

data class RpcRequest(
    val type: RequestType,
    val subscribe: Subscribe?,
    val unsubscribe: Unsubscribe?,
    val accountId: String?,
    val deleteUnbookedBankTransactionId: Long?,
    val mergeTargetAccountId: String?,
    val importBankTransactionsRequest: ImportBankTransactionsRequest?,
    val executeMatcherRequest: ExecuteMatcherRequest?,
    val deleteMatcherId: String?,
    val userStateV2: UserStateV2?,
    val unbookedBankTransactionMatcher: UnbookedBankTransactionMatcher?,
    val removeUnbookedTransactionMatcherId: String?,
    val deleteBookingId: Long?,
    val createOrUpdateBooking: Booking?,
    val createOrUpdateAccount: AccountDto?,
    val bankAccount: BankAccount?,
    val user: User?,
    val deleteUserId: String?,
)

data class RpcResponse(
    val subscriptionCreated: Boolean? = null,
    val subscriptionRemoved: Boolean? = null,

    val importBankTransactionsResult: ImportResult? = null,
    val editedBookingId: Long? = null,
    val error: String? = null,
)

data class ImportBankTransactionsRequest(
    val accountId: String,
    val filename: String,
    val dataBase64: String,
    val duplicationHandlerType: DuplicationHandlerType,
)

enum class DuplicationHandlerType(val duplicationHandler: DuplicationHandler) {
    sameDateAndAmount({ a, b -> a.datetime == b.datetime && a.amountInCents == b.amountInCents }),
    sameDateAmountAndDescription({ a, b -> a.datetime == b.datetime && a.amountInCents == b.amountInCents && a.memo == b.description }),
}
