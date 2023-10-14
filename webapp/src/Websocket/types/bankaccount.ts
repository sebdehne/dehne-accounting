import {InformationElement} from "./InformationElement";
import {AccountDto} from "./accounts";


export interface BankView extends InformationElement {

}

export interface BankAccountView extends InformationElement {
    bank: BankView;
    accountNumber: string;
    closed: boolean;
    transactionsCounterUnbooked: number;
    currentBalance: number;
}

export type BankTransactionRequest = {
    ledgerId: string;
    bankAccountId: string;
    transactionId: number;
}


export interface BankWithAccounts extends InformationElement {
    accounts: BankAccountViewV2[];
}

export type BankAccountViewV2 = {
    account: AccountDto;
    accountNumber?: string;
    openDate: string;
    closeDate?: string;
    balance: number;
    lastKnownTransactionDate?: string;
}
