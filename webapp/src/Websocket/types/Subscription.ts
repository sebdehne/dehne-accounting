import {LedgerView} from "./ledgers";
import {UserView} from "./user";
import {BankAccountView, BankTransactionRequest} from "./bankaccount";
import {LedgerRapportNode, LedgerRapportRequest} from "./ledger_rapport";
import {BankAccountTransactionView, BankTransactionsRequest} from "./banktransactions";
import {CategoryView} from "./categories";


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

export type ReadRequestType = "userInfo" | "getLedgers" | "getBankAccounts" | 'ledgerRapport' | 'getBankTransactions' | 'getBankTransaction' | 'allCategories';

export type ReadRequest = {
    type: ReadRequestType;
    ledgerId?: string;
    ledgerRapportRequest?: LedgerRapportRequest;
    bankTransactionsRequest?: BankTransactionsRequest;
    bankTransactionRequest?: BankTransactionRequest;
}

export type ReadResponse = {
    ledgers?: LedgerView[];
    userView?: UserView;
    bankAccounts?: BankAccountView[];
    ledgerRapport?: LedgerRapportNode[];
    bankTransactions?: BankAccountTransactionView[];
    bankTransaction?: BankAccountTransactionView;
    categories?: CategoryView[];
}

