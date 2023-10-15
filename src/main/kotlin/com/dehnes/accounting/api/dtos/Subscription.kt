package com.dehnes.accounting.api.dtos

import com.dehnes.accounting.api.*
import com.dehnes.accounting.database.*
import com.dehnes.accounting.database.ChangeLogEventType.*
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
    val listensOn: List<ChangeLogEventType>,
    val listensOnV2: List<KClass<out ChangeLogEventTypeV2>> = emptyList(),
) {
    userInfo(listOf(userUpdated)),
    getLedgers(listOf(legderUpdated, legderRemoved, legderAdded, userLedgerAccessChanged)),
    getBankAccounts(listOf(bankAdded, bankUpdated, bankRemoved, bankAccountAdded, bankAccountUpdated, bankAccountRemoved)),

    ledgerRapport(listOf(bookingAdded, bookingRemoved, bookingChanged)),
    getBankTransactions(listOf(bankTransactionAdded, bankTransactionRemoveLast)),
    getBankTransaction(listOf(bankTransactionAdded, bankTransactionRemoveLast)),

    allCategories(listOf(categoryAdded, categoryUpdated, categoryRemoved)),

    userState(listOf(userStateUpdated)),

    getMatchers(listOf(matcherAdded, matcherUpdated, matcherRemoved)),

    getBookings(listOf(bookingAdded, bookingRemoved, bookingChanged)),
    getBooking(listOf(bookingAdded, bookingRemoved, bookingChanged)),


    // V2s
    getUserState(emptyList(), listOf(UserStateUpdated::class)),
    getAllRealms(emptyList(), listOf(RealmChanged::class)),
    getAllAccounts(emptyList(), listOf(AccountsChanged::class)),
    getOverviewRapport(emptyList(), listOf(AccountsChanged::class, BookingsChanged::class, UserStateUpdated::class)),
    getBanksAndAccountsOverview(emptyList(), listOf(AccountsChanged::class, BookingsChanged::class, UnbookedTransactionsChanged::class)),
    getBankAccountTransactions(emptyList(), listOf(BookingsChanged::class, UnbookedTransactionsChanged::class)),
    getUnbookedBankTransactionMatchers(emptyList(), listOf(UnbookedTransactionMatchersChanged::class)),
    getUnbookedBankTransaction(emptyList(), listOf(UnbookedTransactionsChanged::class)),
    ;

}

data class ReadRequest(
    val type: ReadRequestType,
    val accountId: String? = null,
    val ledgerId: String? = null,
    val ledgerRapportRequest: LedgerRapportRequest? = null,
    val bankTransactionsRequest: BankTransactionsRequest? = null,
    val bankTransactionRequest: BankTransactionRequest? = null,
    val getMatchersRequest: GetMatchersRequest? = null,
    val getBookingsRequest: GetBookingsRequest? = null,
    val getBookingId: Long? = null,
    val unbookedBankTransactionReference: UnbookedBankTransactionMatcherService.UnbookedBankTransactionReference? = null,
)

data class ReadResponse(
    val realms: List<Realm>? = null,
    val ledgers: List<LedgerView>? = null,
    val userView: UserView? = null,
    val bankAccounts: List<BankAccountView>? = null,
    val ledgerRapport: List<LedgerRapportNode>? = null,
    val bankTransactions: BankTransactionsResponse? = null,
    val bankTransaction: BankAccountTransactionView? = null,
    val categories: List<CategoryDto>? = null,
    val userState: UserState? = null,
    val userStateV2: UserStateV2? = null,
    val getMatchersResponse: GetMatchersResponse? = null,
    val getBookingsResponse: List<BookingView>? = null,
    val getBookingResponse: BookingView? = null,
    val overViewRapport: List<OverviewRapportAccount>? = null,
    val banksAndAccountsOverview: List<BankWithAccounts>? = null,
    val getBankAccountTransactions: List<BankAccountTransaction>? = null,
    val allAccounts: List<AccountDto>? = null,
    val unbookedBankTransactionMatchers: List<MatchedUnbookedBankTransactionMatcher>? = null,
    val unbookedTransaction: UnbookedTransaction? = null,
)
