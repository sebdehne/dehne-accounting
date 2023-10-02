package com.dehnes.accounting.api.dtos

import com.dehnes.accounting.database.BankAccountDto
import com.dehnes.accounting.database.BankTransaction
import java.time.Instant


enum class TransactionMatcherFilterType(
    val fn: (ba: BankAccountDto, t: BankTransaction, f: TransactionMatcherFilter) -> Boolean
) {
    startsWith({ba: BankAccountDto,  t, f -> t.description?.startsWith(f.pattern!!) ?: false }),
    endsWith({ba: BankAccountDto,  t, f -> t.description?.endsWith(f.pattern!!) ?: false }),
    exact({ba: BankAccountDto,  t, f -> t.description == f.pattern!! }),
    contains({ ba: BankAccountDto, t, f -> t.description?.contains(f.pattern!!) ?: false }),
    amountBetween({ba: BankAccountDto,  t, f ->
        t.amount in (f.fromAmount!!..f.toAmount!!)
    }),
    deposit({ba: BankAccountDto,  t: BankTransaction, f: TransactionMatcherFilter ->
        t.amount > 0
    }),
    withdrawal({ba: BankAccountDto,  t: BankTransaction, _: TransactionMatcherFilter ->
        t.amount < 0
    }),
    ifAccountName({ba: BankAccountDto, t: BankTransaction, f: TransactionMatcherFilter -> ba.name == f.bankAccountName })
}

data class TransactionMatcherFilter(
    val type: TransactionMatcherFilterType,
    val pattern: String? = null,
    val fromAmount: Long? = null,
    val toAmount: Long? = null,
    val bankAccountName: String? = null,
)

enum class TransactionMatcherTargetType {
    multipleCategoriesBooking,
    bankTransfer,
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
    val lastUsed: Instant,
)


data class GetMatchersRequest(
    val ledgerId: String,
    val testMatchFor: TestMatchFor?,
)

data class TestMatchFor(
    val bankAccountId: String,
    val transactionId: Long,
)

data class GetMatchersResponse(
    val machers: List<TransactionMatcher>,
    val macherIdsWhichMatched: List<String>,
)

data class ExecuteMatcherRequest(
    val ledgerId: String,
    val bankAccountId: String,
    val transactionId: Long,
    val matcherId: String,
    val memoText: String?,
)