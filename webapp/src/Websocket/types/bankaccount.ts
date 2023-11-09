import {InformationElement} from "./InformationElement";


export interface BankWithAccounts extends InformationElement {
    accounts: BankAccountView[];
}

export type BankAccountView = {
    accountId: string;
    accountNumber?: string;
    openDate: string;
    closeDate?: string;
    balance: number;
    lastKnownTransactionDate?: string;
    totalUnbooked: number;
}

export type BankAccount = {
    accountId: string;
    bankId: string;
    accountNumber?: string;
    openDate: string;
    closeDate?: string;
}

