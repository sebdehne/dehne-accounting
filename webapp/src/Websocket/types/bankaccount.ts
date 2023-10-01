import {InformationElement} from "./InformationElement";


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
