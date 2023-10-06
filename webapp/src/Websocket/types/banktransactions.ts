export type BankTransactionsRequest = {
    bankAccountId: string;
    from: string;
    toExcluding: string;
}


export type  BankAccountTransactionView = {
    id: number;
    description?: string;
    datetime: string;
    amount: number;
    balance: number;
    matched: boolean;
}

export type BankTransactionsResponse = {
    totalUnmatched: number;
    transactions: BankAccountTransactionView[],
}