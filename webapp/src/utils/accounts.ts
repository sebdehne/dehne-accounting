import {AccountDto} from "../Websocket/types/accounts";


export class Accounts {
    public accounts: AccountDto[];
    public accountsExpanded: AccountExpanded[];

    constructor(accounts: AccountDto[]) {
        this.accounts = accounts;
        this.accountsExpanded = accounts.map(a => buildAccountExpanded(accounts, a));
    }

    hasData(): boolean {
        return this.accounts.length > 0;
    }

    getById(id: string): AccountDto {
        return this.accounts.find(a => a.id === id)!
    }

    generateParentsString(id: string, separator: string = ':'): string {
        return this.accountsExpanded
            .find(a => a.account.id === id)!
            .parentPath
            .map(a => a.name)
            .join(separator)
    }
}

export class AccountExpanded {
    public account: AccountDto;
    public parentPath: AccountDto[];

    constructor(account: AccountDto, parentPath: AccountDto[]) {
        this.account = account;
        this.parentPath = parentPath;
    }

    compare(other: AccountExpanded) {
        const aString = this.parentPath.map(a => a.name).join() + this.account.name
        const bString = other.parentPath.map(a => a.name).join() + other.account.name

        return aString.localeCompare(bString)
    }

    startsWith(path: string[]): boolean {
        let matches = true;
        path.forEach((p, index) => {
            matches = matches && this.parentPath[index]?.name === p
        })
        return matches;
    }
}

const buildAccountExpanded = (accounts: AccountDto[], account: AccountDto): AccountExpanded => {

    function findParents(account: AccountDto, path: AccountDto[]): AccountDto[] {
        if (!account.parentAccountId) return path;
        const parent = accounts.find(a => a.id === account.parentAccountId)!
        return findParents(parent, [parent, ...path]);
    }

    return new AccountExpanded(
        account,
        findParents(account, [])
    );
}
