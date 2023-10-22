import {BankWithAccounts} from "./bankaccount";
import {BankAccountTransaction} from "./banktransactions";
import {Realm} from "./realm";
import {UserStateV2} from "./UserStateV2";
import {OverviewRapportAccount} from "./OverviewRapportAccount";
import {AccountDto} from "./accounts";
import {
    MatchedUnbookedBankTransactionMatcher,
    UnbookedBankTransactionReference,
    UnbookedTransaction
} from "./unbookedTransactions";
import {Booking} from "./bookings";

export type Subscribe = {
    subscriptionId: string;
    readRequest: ReadRequest;
}

export type Unsubscribe = {
    subscriptionId: string;
}

export type Notify = {
    subscriptionId: string;
    readResponse: ReadResponse;
}

export type ReadRequestType = 'getAllRealms'
    | 'getUserState'
    | 'getOverviewRapport'
    | 'getBanksAndAccountsOverview'
    | 'getBankAccountTransactions'
    | 'getAllAccounts'
    | 'getUnbookedBankTransactionMatchers'
    | 'getUnbookedBankTransaction'
    | 'getTotalUnbookedTransactions'
    | 'getBookings'
    | 'getBooking'
    ;

export type ReadRequest = {
    type: ReadRequestType;
    accountId?: string;
    getBookingId?: number;
    unbookedBankTransactionReference?: UnbookedBankTransactionReference;
}

export type ReadResponse = {
    realms?: Realm[];
    userStateV2?: UserStateV2;
    overViewRapport?: OverviewRapportAccount[];
    banksAndAccountsOverview?: BankWithAccounts[];
    getBankAccountTransactions?: BankAccountTransaction[];
    allAccounts?: AccountDto[];
    unbookedBankTransactionMatchers?: MatchedUnbookedBankTransactionMatcher[];
    unbookedTransaction?: UnbookedTransaction;
    totalUnbookedTransactions?: number;
    bookings?: Booking[];
    booking?: Booking;
}

