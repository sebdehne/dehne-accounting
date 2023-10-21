

export type LocalState = {
    accountTree: AccountTree
}


export class AccountTree {
    private expandedAccountIds: string[];

    constructor(init: string[] = []) {
        this.expandedAccountIds = init;
    }

    toggle(accountId: string): AccountTree {
        if (this.isExpanded(accountId)) {
            return this.collaps(accountId);
        } else {
            return this.expand(accountId);
        }
    }

    expand(accountId: string): AccountTree {
        return new AccountTree([...this.expandedAccountIds.filter(id => id !== accountId), accountId])
    }

    collaps(accountId: string): AccountTree {
        return new AccountTree([...this.expandedAccountIds.filter(id => id !== accountId)])
    }

    isExpanded(accountId: string) {
        return this.expandedAccountIds.includes(accountId)
    }
}
