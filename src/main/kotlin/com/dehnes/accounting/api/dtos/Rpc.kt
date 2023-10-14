package com.dehnes.accounting.api.dtos

import com.dehnes.accounting.bank.importers.DuplicationHandler
import com.dehnes.accounting.bank.importers.ImportResult
import com.dehnes.accounting.database.BookingView
import com.dehnes.accounting.database.CategoryDto


enum class RequestType {
    subscribe,
    unsubscribe,

    removeLastBankTransaction,

    addOrReplaceMatcher,
    deleteMatcher,
    executeMatcher,

    addOrReplaceBooking,
    removeBooking,

    setUserState,

    addOrReplaceCategory,
    mergeCategories,

    // V2
    setUserStateV2,
    importBankTransactions,

}

data class RpcRequest(
    val type: RequestType,
    val subscribe: Subscribe?,
    val unsubscribe: Unsubscribe?,
    val ledgerId: String?,
    val bankAccountId: String?,
    val importBankTransactionsRequest: ImportBankTransactionsRequest?,
    val addOrReplaceMatcherRequest: TransactionMatcher?,
    val executeMatcherRequest: ExecuteMatcherRequest?,
    val userState: UserState?,
    val deleteMatcherId: String?,
    val bookingId: Long?,
    val addOrReplaceCategory: CategoryDto?,
    val mergeCategoriesRequest: MergeCategoriesRequest?,
    val addOrReplaceBooking: BookingView?,
    val userStateV2: UserStateV2?,
)

data class RpcResponse(
    val subscriptionCreated: Boolean? = null,
    val subscriptionRemoved: Boolean? = null,

    val importBankTransactionsResult: ImportResult? = null,
    val getMatchCandidatesResult: List<TransactionMatcher>? = null,
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
