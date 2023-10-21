export type Booking = {
    realmId: string;
    id: number;
    description?: string;
    datetime?: string;
    entries: BookingEntry[];
}

export type  BookingEntry = {
    id: number;
    description?: string;
    accountId: string;
    amountInCents: number;
}
