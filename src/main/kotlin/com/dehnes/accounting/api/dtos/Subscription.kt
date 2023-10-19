package com.dehnes.accounting.api.dtos

import com.dehnes.accounting.api.*
import com.dehnes.accounting.database.*
import com.dehnes.accounting.services.MatchedUnbookedBankTransactionMatcher
import com.dehnes.accounting.services.OverviewRapportAccount
import com.dehnes.accounting.services.UnbookedBankTransactionMatcherService
import com.dehnes.accounting.services.bank.BankAccountTransaction
import com.dehnes.accounting.services.bank.BankWithAccounts
import kotlin.reflect.KClass


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
    val listensOnV2: List<KClass<out ChangeLogEventTypeV2>> = emptyList(),
) {
    getAllRealms(listOf(RealmChanged::class)),
    getUserState(listOf(UserStateUpdated::class)),
    getOverviewRapport(listOf(AccountsChanged::class, BookingsChanged::class, UserStateUpdated::class)),
    getBanksAndAccountsOverview(listOf(AccountsChanged::class, BookingsChanged::class, UnbookedTransactionsChanged::class)),
    getBankAccountTransactions(listOf(BookingsChanged::class, UnbookedTransactionsChanged::class)),
    getAllAccounts(listOf(AccountsChanged::class)),
    getUnbookedBankTransactionMatchers(listOf(UnbookedTransactionMatchersChanged::class)),
    getUnbookedBankTransaction(listOf(UnbookedTransactionsChanged::class)),
    getTotalUnbookedTransactions(listOf(UnbookedTransactionsChanged::class)),
    ;

}

data class ReadRequest(
    val type: ReadRequestType,
    val accountId: String? = null,
    val ledgerId: String? = null,
    val getBookingId: Long? = null,
    val unbookedBankTransactionReference: UnbookedBankTransactionMatcherService.UnbookedBankTransactionReference? = null,
)

data class ReadResponse(
    val realms: List<Realm>? = null,
    val userState: UserState? = null,
    val userStateV2: UserStateV2? = null,
    val overViewRapport: List<OverviewRapportAccount>? = null,
    val banksAndAccountsOverview: List<BankWithAccounts>? = null,
    val getBankAccountTransactions: List<BankAccountTransaction>? = null,
    val allAccounts: List<AccountDto>? = null,
    val unbookedBankTransactionMatchers: List<MatchedUnbookedBankTransactionMatcher>? = null,
    val unbookedTransaction: UnbookedTransaction? = null,
    val totalUnbookedTransactions: Long? = null,
)
