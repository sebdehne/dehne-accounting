import {AccountDto, AllAccounts, StandardAccountView} from "../Websocket/types/accounts";
import {BudgetType} from "../Websocket/types/budget";


export class Accounts {
    public accounts: AccountDto[];
    public tree: AccountExpanded[];
    public flat: AccountExpanded[];
    public byId: { [key: string]: AccountExpanded };
    public standardAccounts: StandardAccountView[];

    constructor(allAccounts: AllAccounts) {
        this.accounts = allAccounts.allAccounts;
        const [tree, flat] = buildAccountExpanded(this.accounts);
        this.tree = tree;
        this.flat = flat;
        this.byId = Object.fromEntries(this.flat.map(a => ([a.account.id, a])));
        this.standardAccounts = allAccounts.standardAccounts;
    }

    getStandardAccountName(originalName: string): string {
        const s = this.standardAccounts.find(s => s.originalName === originalName)!
        return this.getById(s.id)!.name
    }

    hasData(): boolean {
        return this.accounts.length > 0;
    }

    getById(id: string): AccountDto | undefined {
        let accountExpanded = this.byId[id];
        if (!accountExpanded) {
            console.log("Could not find " + id);
            console.log(this.byId);
        }
        return accountExpanded?.account;
    }

    getBudgetType(id: string): BudgetType | undefined {
        let current = this.getById(id);
        if (!current) {
            return undefined
        }
        if (current.budgetType) {
            return current.budgetType;
        }
        if (current.parentAccountId) {
            return this.getBudgetType(current.parentAccountId);
        }
        return undefined;
    }

    getByIdExpanded(id: string): AccountExpanded {
        return this.byId[id];
    }

    generateParentsString(id: string, separator: string = ':'): string {
        return this.byId[id]
            .parentPath
            .map(a => a.name)
            .join(separator)
    }
}

export class AccountExpanded {
    public account: AccountDto;
    public parentPath: AccountDto[];
    public children: AccountExpanded[];

    constructor(account: AccountDto, parentPath: AccountDto[], children: AccountExpanded[]) {
        this.account = account;
        this.parentPath = parentPath;
        this.children = children;
    }

    compare(other: AccountExpanded) {
        const aString = this.parentPath.map(a => a.name).join() + this.account.name
        const bString = other.parentPath.map(a => a.name).join() + other.account.name

        return aString.localeCompare(bString)
    }

    filteredChildren(filter: string): AccountExpanded[] | undefined {
        if (filter) {
            const filteredChildren = this.children.filter(c => c.filteredChildren(filter) !== undefined)

            if (filteredChildren.length > 0 || this.account.name.toLowerCase().includes(filter.toLowerCase())) {
                return filteredChildren;
            } else {
                return undefined;
            }
        } else {
            return this.children;
        }
    }

    startsWith(path: string[]): boolean {
        let matches = true;
        path.forEach((p, index) => {
            matches = matches && this.parentPath[index]?.name === p
        })
        return matches;
    }
}

const buildAccountExpanded = (accounts: AccountDto[]): AccountExpanded[][] => {

    function findParents(account: AccountDto, path: AccountDto[]): AccountDto[] {
        if (!account.parentAccountId) return path;
        const parent = accounts.find(a => a.id === account.parentAccountId)!
        return findParents(parent, [parent, ...path]);
    }

    const flatList: AccountExpanded[] = [];

    function buildChild(account: AccountDto): AccountExpanded {
        let accountExpanded = new AccountExpanded(
            account,
            findParents(account, []),
            accounts.filter(a => a.parentAccountId === account.id).map(a => buildChild(a))
        );
        flatList.unshift(accountExpanded);
        return accountExpanded
    }

    const roots = accounts.filter(a => !a.parentAccountId).map(a => buildChild(a));

    return [roots, flatList];
}
