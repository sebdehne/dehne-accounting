import {LedgerView} from "./ledgers";
import {UserView} from "./user";
import {BankAccountView, BankTransactionRequest} from "./bankaccount";
import {LedgerRapportNode, LedgerRapportRequest} from "./ledger_rapport";
import {BankAccountTransactionView, BankTransactionsRequest} from "./banktransactions";
import {CategoryDto} from "./categories";
import {UserState} from "../../utils/userstate";
import {GetMatchersRequest, GetMatchersResponse} from "./transactionMatcher";
import {BookingView, GetBookingsRequest} from "./bookings";


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
    ;

export type ReadRequest = {
    type: ReadRequestType;
    ledgerId?: string;
    ledgerRapportRequest?: LedgerRapportRequest;
    bankTransactionsRequest?: BankTransactionsRequest;
    bankTransactionRequest?: BankTransactionRequest;
    getMatchersRequest?: GetMatchersRequest;
    getBookingsRequest?: GetBookingsRequest;
    getBookingId?: number;
}

export type ReadResponse = {
    ledgers?: LedgerView[];
    userView?: UserView;
    bankAccounts?: BankAccountView[];
    ledgerRapport?: LedgerRapportNode[];
    bankTransactions?: BankAccountTransactionView[];
    bankTransaction?: BankAccountTransactionView;
    categories?: CategoryDto[];
    userState?: UserState;
    getMatchersResponse?: GetMatchersResponse;
    getBookingsResponse?: BookingView[];
    getBookingResponse?: BookingView;
}

