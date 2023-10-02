package com.dehnes.accounting.api.dtos

import com.dehnes.accounting.database.BankTransaction


enum class TransactionMatcherFilterType(
    val fn: (t: BankTransaction, f: TransactionMatcherFilter) -> Boolean
) {
    startsWith({ t, f -> t.description?.startsWith(f.pattern!!) ?: false }),
    endsWith({ t, f -> t.description?.endsWith(f.pattern!!) ?: false }),
    exact({ t, f -> t.description == f.pattern!! }),
    contains({ t, f -> t.description?.contains(f.pattern!!) ?: false }),
    amountBetween({ t, f ->
        t.amount in (f.fromAmount!!..f.toAmount!!)
    }),
}

data class TransactionMatcherFilter(
    val type: TransactionMatcherFilterType,
    val pattern: String? = null,
    val fromAmount: Long? = null,
    val toAmount: Long? = null,
)

enum class TransactionMatcherTargetType {
    multipleCategoriesBooking,
    bankTransferReceived,
    bankTransferSent,
}

data class TransactionMatcherTarget(
    val type: TransactionMatcherTargetType,
    val transferCategoryId: String? = null,
    val multipleCategoriesBooking: MultipleCategoriesBookingWrapper? = null,
)

data class MultipleCategoriesBookingWrapper(
    val debitRules: List<BookingRule>,
    val creditRules: List<BookingRule>,
)

enum class BookingRuleType {
    categoryBookingRemaining,
    categoryBookingFixedAmount,
}
data class BookingRule(
    val type: BookingRuleType,
    val categoryId: String,
    val amountInCents: Long? = null,
)

data class TransactionMatcher(
    val id: String,
    val ledgerId: String,
    val name: String,
    val filters: List<TransactionMatcherFilter>,
    val target: TransactionMatcherTarget,
)


data class GetMatchCandidatesRequest(
    val ledgerId: String,
    val bankAccountId: String,
    val transactionId: Long,
)

data class ExecuteMatcherRequest(
    val ledgerId: String,
    val bankAccountId: String,
    val transactionId: Long,
    val matcherId: String,
)