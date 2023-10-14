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

export type BankAccountTransaction = {
    bookingReference?: BookingReference;
    unbookedReference?: UnbookedReference;
    datetime: string;
    memo?: string;
    amountInCents: number;
    balance: number;
}

export type BookingReference = {
    bookingId: number;
    bookingEntryId: number;
    otherAccountId: string;
}

export type UnbookedReference = {
    unbookedId: number;
    otherAccountNumber?: string;
}
