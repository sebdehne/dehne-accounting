import {InformationElement} from "./InformationElement";


export interface AccountDto extends InformationElement {
    parentAccountId?: string;
    partyId?: string;
}

export const isAccountPayable = (path: AccountDto[]): boolean => matchedPath(path, ['Liability', 'AccountPayable'])

export const isAccountReceivable = (path: AccountDto[]): boolean => matchedPath(path, ['Asset', 'AccountReceivable'])

export const isBankAccountAsset = (path: AccountDto[]): boolean => matchedPath(path, ['Asset', 'BankAccountAsset'])

export const isBankAccountLiability = (path: AccountDto[]): boolean => matchedPath(path, ['Liability', 'BankAccountLiability'])

const matchedPath = (path: AccountDto[], expected: StandardAccount[]): boolean => {
    const pathNames  = path.map(a => a.name);
    let matches = true;
    expected.forEach((p, index) => matches = matches && pathNames[index] === p)
    return matches;
}

export type StandardAccount = 'Asset'
    | 'Liability'
    | 'Equity'
    | 'Income'
    | 'Expense'
    | 'AccountPayable'
    | 'AccountReceivable'
    | 'OpeningBalances'
    | 'OtherBankTransfers'
    | 'BankAccountAsset'
    | 'BankAccountLiability'
    ;
