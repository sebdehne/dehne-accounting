import {LedgerView} from "./ledgers";
import {UserView} from "./user";
import {BankAccountView, BankTransactionRequest, BankWithAccounts} from "./bankaccount";
import {LedgerRapportNode, LedgerRapportRequest} from "./ledger_rapport";
import {
    BankAccountTransaction,
    BankAccountTransactionView,
    BankTransactionsRequest,
    BankTransactionsResponse
} from "./banktransactions";
import {CategoryDto} from "./categories";
import {UserState} from "../../utils/userstate";
import {GetMatchersRequest, GetMatchersResponse} from "./transactionMatcher";
import {BookingView, GetBookingsRequest} from "./bookings";
import {Realm} from "./realm";
import {UserStateV2} from "./UserStateV2";
import {OverviewRapportAccount} from "./OverviewRapportAccount";
import {AccountDto} from "./accounts";


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

export type ReadRequestType = "userInfo"
    | "getLedgers"
    | "getBankAccounts"
    | 'ledgerRapport'
    | 'getBankTransactions'
    | 'getBankTransaction'
    | 'allCategories'
    | 'userState'
    | 'getMatchers'
    | 'getBookings'
    | 'getBooking'
    | 'getUserState'
    | 'getAllRealms'
    | 'getOverviewRapport'
    | 'getBanksAndAccountsOverview'
    | 'getBankAccountTransactions'
    | 'getAllAccounts'
    ;

export type ReadRequest = {
    type: ReadRequestType;
    ledgerId?: string;
    accountId?: string;
    ledgerRapportRequest?: LedgerRapportRequest;
    bankTransactionsRequest?: BankTransactionsRequest;
    bankTransactionRequest?: BankTransactionRequest;
    getMatchersRequest?: GetMatchersRequest;
    getBookingsRequest?: GetBookingsRequest;
    getBookingId?: number;
}

export type ReadResponse = {
    realms?: Realm[];
    ledgers?: LedgerView[];
    userView?: UserView;
    bankAccounts?: BankAccountView[];
    ledgerRapport?: LedgerRapportNode[];
    bankTransactions?: BankTransactionsResponse;
    bankTransaction?: BankAccountTransactionView;
    categories?: CategoryDto[];
    userState?: UserState;
    userStateV2?: UserStateV2;
    getMatchersResponse?: GetMatchersResponse;
    getBookingsResponse?: BookingView[];
    getBookingResponse?: BookingView;
    overViewRapport?: OverviewRapportAccount[];

    banksAndAccountsOverview?: BankWithAccounts[];
    getBankAccountTransactions?: BankAccountTransaction[];
    allAccounts?: AccountDto[];
}

