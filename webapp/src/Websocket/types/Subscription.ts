import {BankAccount, BankWithAccounts} from "./bankaccount";
import {BankAccountTransaction} from "./banktransactions";
import {OverviewRapportAccount} from "./OverviewRapportAccount";
import {
    MatchedUnbookedBankTransactionMatcher,
    UnbookedBankTransactionReference,
    UnbookedTransaction
} from "./unbookedTransactions";
import {Booking} from "./bookings";
import {User} from "./User";
import {InformationElement} from "./InformationElement";
import {GlobalState} from "./globalstate";

export type Subscribe = {
    subscriptionId: string;
    readRequest: ReadRequest;
}

export type Unsubscribe = {
    subscriptionId: string;
}

export type Notify = {
    subscriptionId: string;
    readResponse?: ReadResponse;
    generatingNotify?: boolean;
}

export type ReadRequestType =
    'getGlobalState'
    | 'getAllUsers'
    | 'getOverviewRapport'
    | 'getBanksAndAccountsOverview'
    | 'getUnbookedBankTransactionMatchers'
    | 'getUnbookedBankTransaction'
    | 'getTotalUnbookedTransactions'
    | 'getBookings'
    | 'getBooking'
    | 'getBankAccount'
    | 'listBackups'
    ;

export type ReadRequest = {
    type: ReadRequestType;
    accountId?: string;
    getBookingId?: number;
    unbookedBankTransactionReference?: UnbookedBankTransactionReference;
}

export type ReadResponse = {
    globalState?: GlobalState;
    overViewRapport?: OverviewRapportAccount[];
    banksAndAccountsOverview?: BankWithAccounts[];
    getBankAccountTransactions?: BankAccountTransaction[];
    unbookedBankTransactionMatchers?: MatchedUnbookedBankTransactionMatcher[];
    unbookedTransaction?: UnbookedTransaction;
    totalUnbookedTransactions?: number;
    bookings?: Booking[];
    bookingsBalance?: number;
    booking?: Booking;
    bankAccount?: BankAccount;
    allUsers?: AllUsersInfo;
    backups?: string[];
}

export type AllUsersInfo = {
    allUsers: User[];
    allRealms: RealmInfo[];
}

export type RealmInfo = InformationElement & {
    closure: string
}
