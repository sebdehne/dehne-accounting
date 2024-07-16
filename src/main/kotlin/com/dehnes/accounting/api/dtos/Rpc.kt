package com.dehnes.accounting.api.dtos

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
    updateChecked,

    mergeAccount,
    createOrUpdateAccount,

    deleteBankAccount,
    createOrUpdateBankAccount,

    closeNextMonth,
    reopenPreviousMonth,

    // admin commands
    addOrReplaceUser,
    deleteUser,
    createNewBackup,
    restoreBackup,
    dropBackup,
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
    val bookingId: Long?,
    val bookingEntryId: Long?,
    val bookingEntryChecked: Boolean?,
    val createOrUpdateBooking: Booking?,
    val createOrUpdateAccount: AccountDto?,
    val bankAccount: BankAccount?,
    val user: User?,
    val deleteUserId: String?,
    val backupName: String?,
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
)

