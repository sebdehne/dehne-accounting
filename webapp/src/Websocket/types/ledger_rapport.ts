

export type LedgerRapportRequest = {
    from: string;
    toExcluding: string;
}

export type LedgerRapportNode = {
    accountName: string;
    totalAmountInCents: number;
    bookingRecords?: LedgerRapportBookingRecord[];
    children?: LedgerRapportNode[];
}


export type LedgerRapportBookingRecord = {
    bookingId: number;
    id: number;
    datetime: string;
    amountInCents: number;
    description?: string;
    contraRecords?: LedgerRapportBookingContraRecord[];
}


export type LedgerRapportBookingContraRecord = {
    accountName: string;
    bookingId: number;
    id: number;
}

