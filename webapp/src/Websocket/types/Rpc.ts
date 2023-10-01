import {Subscribe, Unsubscribe} from "./Subscription";
import {ExecuteMatcherRequest, GetMatchCandidatesRequest, TransactionMatcher} from "./transactionMatcher";

export type RequestType = "subscribe" | "unsubscribe" | 'importBankTransactions' | 'getMatchCandidates' |
    'addNewMatcher' |
    'executeMatcher';

export type RpcRequest = {
    type: RequestType;
    subscribe?: Subscribe;
    unsubscribe?: Unsubscribe;

    importBankTransactionsRequest?: ImportBankTransactionsRequest;
    addNewMatcherRequest?: TransactionMatcher;
    getMatchCandidatesRequest?: GetMatchCandidatesRequest;
    executeMatcherRequest?: ExecuteMatcherRequest;
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
