import {Subscribe, Unsubscribe} from "./Subscription";
import {ExecuteMatcherRequest} from "./transactionMatcher";
import {UserStateV2} from "./UserStateV2";
import {UnbookedBankTransactionMatcher} from "./unbookedTransactions";

export type RequestType = "subscribe"
    | "unsubscribe"
    | 'importBankTransactions'
    | 'setUserStateV2'
    | 'deleteAllUnbookedTransactions'
    | 'addOrReplaceUnbookedTransactionMatcher'
    | 'removeUnbookedTransactionMatcher'
    | 'executeMatcherUnbookedTransactionMatcher'
    ;

export type RpcRequest = {
    type: RequestType;
    subscribe?: Subscribe;
    unsubscribe?: Unsubscribe;

    accountId?: string;
    importBankTransactionsRequest?: ImportBankTransactionsRequest;
    executeMatcherRequest?: ExecuteMatcherRequest;
    deleteMatcherId?: string;
    userStateV2?: UserStateV2;
    unbookedBankTransactionMatcher?: UnbookedBankTransactionMatcher;
    removeUnbookedTransactionMatcherId?: string;
}

export type RpcResponse = {
    subscriptionCreated?: boolean;
    subscriptionRemoved?: boolean;

    importBankTransactionsResult?: ImportBankTransactionsResult;
    error?: string;
}

export type ImportBankTransactionsRequest = {
    accountId: string;
    filename: string;
    dataBase64: string;
    duplicationHandlerType: DuplicationHandlerType;
}

export type ImportBankTransactionsResult = {
    imported: number;
    skipped: number;
}

export type DuplicationHandlerType = 'sameDateAndAmount' | 'sameDateAmountAndDescription'
