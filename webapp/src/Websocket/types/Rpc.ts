import {Subscribe, Unsubscribe} from "./Subscription";

export type RequestType = "subscribe" | "unsubscribe" | 'importBankTransactions';

export type RpcRequest = {
    type: RequestType;
    subscribe?: Subscribe;
    unsubscribe?: Unsubscribe;

    importBankTransactionsRequest?: ImportBankTransactionsRequest;
}

export type RpcResponse = {
    subscriptionCreated?: boolean;
    subscriptionRemoved?: boolean;

    importBankTransactionsResult?: ImportBankTransactionsResult;
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
    error?: string;
}

export type DuplicationHandlerType = 'sameDateAndAmount' | 'sameDateAmountAndDescription'
