package com.dehnes.accounting.api.dtos

import com.dehnes.accounting.database.BankAccountDto
import com.dehnes.accounting.database.BankTransaction
import java.time.Instant


@Suppress("UNUSED_ANONYMOUS_PARAMETER")
enum class TransactionMatcherFilterType(
    val fn: (ba: BankAccountDto, t: BankTransaction, f: TransactionMatcherFilter) -> Boolean
) {
    startsWith({ba: BankAccountDto,  t, f -> t.description?.startsWith(f.pattern!!) ?: false }),
    endsWith({ba: BankAccountDto,  t, f -> t.description?.endsWith(f.pattern!!) ?: false }),
    exact({ba: BankAccountDto,  t, f -> t.description == f.pattern!! }),
    contains({ ba: BankAccountDto, t, f -> t.description?.contains(f.pattern!!) ?: false }),
    amountBetween({ba: BankAccountDto,  t, f ->
        val range = if (f.fromAmount!! < f.toAmount!!) {
            (f.fromAmount..f.toAmount)
        } else {
            (f.toAmount..f.fromAmount)
        }
        t.amount in range
    }),
    income({ba: BankAccountDto,  t: BankTransaction, f: TransactionMatcherFilter ->
        t.amount > 0
    }),
    payment({ba: BankAccountDto,  t: BankTransaction, _: TransactionMatcherFilter ->
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

enum class TransactionMatcherActionType {
    paymentOrIncome,
    bankTransfer,
}

data class TransactionMatcherAction(
    val type: TransactionMatcherActionType,
    val transferCategoryId: String? = null,
    val paymentOrIncomeConfig: PaymentOrIncomeConfig? = null,
)

data class PaymentOrIncomeConfig(
    val mainSide: BookingConfigurationForOneSide,
    val negatedSide: BookingConfigurationForOneSide,
)

data class BookingConfigurationForOneSide(
    val categoryToFixedAmountMapping: Map<String, Long>,
    val categoryIdRemaining: String,
)

data class TransactionMatcher(
    val id: String,
    val ledgerId: String,
    val name: String,
    val filters: List<TransactionMatcherFilter>,
    val action: TransactionMatcherAction,
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

// V2
data class ExecuteMatcherRequest(
    val accountId: String,
    val transactionId: Long,
    val matcherId: String,
    val overrideMemo: String?,
)
