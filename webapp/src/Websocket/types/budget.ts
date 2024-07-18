export type BudgetRule = {
    realmId: string;
    accountId: string;
    month: number;
    amountInCents: number;
}

export type UpdateBudget = {
    accountId: string;
    budget: { [key: string]: number };
}

export type BudgetType = 'min' | 'max';

