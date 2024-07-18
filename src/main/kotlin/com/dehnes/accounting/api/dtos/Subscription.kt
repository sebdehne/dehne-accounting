package com.dehnes.accounting.api.dtos

import com.dehnes.accounting.api.*
import com.dehnes.accounting.database.*
import com.dehnes.accounting.services.*
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
    val readResponse: ReadResponse?,
    val generatingNotify: Boolean?,
)

enum class ReadRequestType(
    val listensOnV2: List<KClass<out ChangeLogEventTypeV2>> = emptyList(),
) {

    getGlobalState(listOf(UserUpdated::class, UserStateUpdated::class, RealmChanged::class, AccountsChanged::class)),
    getAllUsers(listOf(UserUpdated::class)),
    getOverviewRapport(listOf(AccountsChanged::class, BookingsChanged::class, UserStateUpdated::class)),
    getBanksAndAccountsOverview(
        listOf(
            AccountsChanged::class,
            BookingsChanged::class,
            BankAccountChanged::class,
            UnbookedTransactionsChanged::class,
            UserStateUpdated::class,
        )
    ),
    getUnbookedBankTransactionMatchers(listOf(UnbookedTransactionMatchersChanged::class, UserStateUpdated::class)),
    getUnbookedBankTransaction(listOf(UnbookedTransactionsChanged::class, UserStateUpdated::class)),
    getTotalUnbookedTransactions(listOf(UnbookedTransactionsChanged::class, UserStateUpdated::class)),
    getBookings(listOf(BookingsChanged::class, UserStateUpdated::class, UnbookedTransactionsChanged::class)),
    getBooking(listOf(BookingsChanged::class, UserStateUpdated::class)),
    getBankAccount(listOf(BankAccountChanged::class, UserStateUpdated::class)),

    getBudgetRulesForAccount(listOf(BudgetChanged::class)),
    getBudgetAccounts(listOf(BudgetChanged::class)),

    listBackups(listOf(DatabaseBackupChanged::class)),

    ;

}

data class ReadRequest(
    val type: ReadRequestType,
    val accountId: String? = null,
    val getBookingId: Long? = null,
    val unbookedBankTransactionReference: UnbookedBankTransactionMatcherService.UnbookedBankTransactionReference? = null,
)

data class ReadResponse(
    val globalState: GlobalState? = null,
    val overViewRapport: List<OverviewRapportAccount>? = null,
    val banksAndAccountsOverview: List<BankWithAccounts>? = null,
    val unbookedBankTransactionMatchers: List<MatchedUnbookedBankTransactionMatcher>? = null,
    val unbookedTransaction: UnbookedTransaction? = null,
    val totalUnbookedTransactions: Long? = null,
    val bookings: List<Booking>? = null,
    val bookingsBalance: Long? = null,
    val booking: Booking? = null,
    val bankAccount: BankAccount? = null,
    val allUsers: AllUsersInfo? = null,
    val backups: List<String>? = null,
    val budgetRules: List<BudgetDbRecord>? = null,
    val budgetAccounts: List<String>? = null,
)

data class GlobalState(
    val user: User,
    val userStateV2: UserStateV2,
    val globalStateForRealm: GlobalStateForRealm?,
)

data class GlobalStateForRealm(
    val selectedRealmInfo: RealmInfo,
    val allAccounts: AllAccounts,
)

data class AllUsersInfo(
    val allUsers: List<User>,
    val allRealms: List<RealmInfo>,
)
