import {InformationElement} from "./InformationElement";
import {Accounts} from "../../utils/accounts";


export interface AccountDto extends InformationElement {
    parentAccountId?: string;
    partyId?: string;
    builtIn: boolean;
    realmId: string;
}

export const isAccountPayable = (accounts: Accounts, path: AccountDto[]): boolean => matchedPath(path, [
    accounts.getStandardAccountName('Liability'),
    accounts.getStandardAccountName('AccountPayable')
])

export const isAccountReceivable = (accounts: Accounts, path: AccountDto[]): boolean => matchedPath(path, [
    accounts.getStandardAccountName('Asset'),
    accounts.getStandardAccountName('AccountReceivable'),
])

export const isBankAccountAsset = (accounts: Accounts, path: AccountDto[]): boolean => matchedPath(path, [
    accounts.getStandardAccountName('Asset'),
    accounts.getStandardAccountName('BankAccountAsset')
])

export const isBankAccountLiability = (accounts: Accounts, path: AccountDto[]): boolean => matchedPath(path, [
    accounts.getStandardAccountName('Liability'),
    accounts.getStandardAccountName('BankAccountLiability'),
])

const matchedPath = (path: AccountDto[], expected: string[]): boolean => {
    const pathNames = path.map(a => a.name);
    let matches = true;
    expected.forEach((p, index) => matches = matches && pathNames[index] === p)
    return matches;
}

export type AllAccounts = {
    allAccounts: AccountDto[];
    standardAccounts: StandardAccountView[];
}

export type StandardAccountView = {
    id: string;
    originalName: string;
    parentAccountId?: string;
}

export type Party = InformationElement & {
    realmId: string;
}