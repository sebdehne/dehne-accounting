import {UnbookedTransaction} from "./unbookedTransactions";

export type Booking = {
    realmId: string;
    id: number;
    description?: string;
    datetime: string;
    entries: BookingEntry[];
    unbookedAmountInCents?: number;
    originalUnbookedTransaction?: UnbookedTransaction
}

export type  BookingEntry = {
    id: number;
    description?: string;
    accountId: string;
    amountInCents: number;
    checked: boolean;
}
