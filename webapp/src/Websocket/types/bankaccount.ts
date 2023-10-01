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

