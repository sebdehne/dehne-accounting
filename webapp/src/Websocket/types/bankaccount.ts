import {InformationElement} from "./InformationElement";
import {AccountDto} from "./accounts";


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
    totalUnbooked: number;
}
