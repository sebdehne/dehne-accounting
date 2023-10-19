

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
