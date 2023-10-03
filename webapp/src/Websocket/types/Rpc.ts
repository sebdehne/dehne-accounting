import {Subscribe, Unsubscribe} from "./Subscription";
import {ExecuteMatcherRequest, TransactionMatcher} from "./transactionMatcher";
import {UserState} from "../../utils/userstate";

export type RequestType = "subscribe"
    | "unsubscribe"
    | 'importBankTransactions'
    | 'addOrReplaceMatcher'
    | 'deleteMatcher'
    | 'executeMatcher'
    | 'setUserState'
    | 'removeBooking'
    ;

export type RpcRequest = {
    type: RequestType;
    subscribe?: Subscribe;
    unsubscribe?: Unsubscribe;

    ledgerId?: string;
    importBankTransactionsRequest?: ImportBankTransactionsRequest;
    addOrReplaceMatcherRequest?: TransactionMatcher;
    executeMatcherRequest?: ExecuteMatcherRequest;
    userState?: UserState;
    deleteMatcherId?: string;
    bookingId?: number;
}

export type RpcResponse = {
    subscriptionCreated?: boolean;
    subscriptionRemoved?: boolean;

    importBankTransactionsResult?: ImportBankTransactionsResult;
    getMatchCandidatesResult?: TransactionMatcher[];
    error?: string;
}

export type ImportBankTransactionsRequest = {
    ledgerId: string;
    bankAccountId: string;
    filename: string;
    dataBase64: string;
    duplicationHandlerType: DuplicationHandlerType;
}

export type ImportBankTransactionsResult = {
    imported: number;
    skipped: number;
}

export type DuplicationHandlerType = 'sameDateAndAmount' | 'sameDateAmountAndDescription'
