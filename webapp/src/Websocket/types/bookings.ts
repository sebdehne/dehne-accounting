export type Booking = {
    realmId: string;
    id: number;
    description?: string;
    datetime: string;
    entries: BookingEntry[];
    unbookedAmountInCents?: number;
}

export type  BookingEntry = {
    id: number;
    description?: string;
    accountId: string;
    amountInCents: number;
    checked: boolean;
}
