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

    // admin commands
    getAllUsers(listOf(UserUpdated::class)),

    //
    getUserInfo(listOf(UserUpdated::class)),

    getUserState(listOf(UserStateUpdated::class)),

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
    getBankAccountTransactions(
        listOf(
            BookingsChanged::class,
            BankAccountChanged::class,
            UnbookedTransactionsChanged::class,
            UserStateUpdated::class,
        )
    ),
    getAllAccounts(listOf(AccountsChanged::class, UserStateUpdated::class)),
    getUnbookedBankTransactionMatchers(listOf(UnbookedTransactionMatchersChanged::class, UserStateUpdated::class,)),
    getUnbookedBankTransaction(listOf(UnbookedTransactionsChanged::class, UserStateUpdated::class,)),
    getTotalUnbookedTransactions(listOf(UnbookedTransactionsChanged::class, UserStateUpdated::class,)),
    getBookings(listOf(BookingsChanged::class, UserStateUpdated::class)),
    getBooking(listOf(BookingsChanged::class, UserStateUpdated::class)),
    getBankAccount(listOf(BankAccountChanged::class, UserStateUpdated::class,)),
    ;

}

data class ReadRequest(
    val type: ReadRequestType,
    val accountId: String? = null,
    val getBookingId: Long? = null,
    val unbookedBankTransactionReference: UnbookedBankTransactionMatcherService.UnbookedBankTransactionReference? = null,
)

data class ReadResponse(
    val realms: List<Realm>? = null,
    val userInfo: UserInfo? = null,
    val userState: UserState? = null,
    val userStateV2: UserStateV2? = null,
    val overViewRapport: List<OverviewRapportAccount>? = null,
    val banksAndAccountsOverview: List<BankWithAccounts>? = null,
    val getBankAccountTransactions: List<BankAccountTransaction>? = null,
    val allAccounts: AllAccounts? = null,
    val unbookedBankTransactionMatchers: List<MatchedUnbookedBankTransactionMatcher>? = null,
    val unbookedTransaction: UnbookedTransaction? = null,
    val totalUnbookedTransactions: Long? = null,
    val bookings: List<Booking>? = null,
    val booking: Booking? = null,
    val bankAccount: BankAccount? = null,
    val allUsers: List<User>? = null,
)

