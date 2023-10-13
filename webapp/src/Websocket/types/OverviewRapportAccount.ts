

export type OverviewRapportAccount = {
    name: string;
    openBalance: number;
    thisPeriod: number;
    closeBalance: number;
    children: OverviewRapportAccount[];
    records: OverviewRapportEntry[];
}

export type OverviewRapportEntry = {
    bookingId: number;
    bookingEntryId: number;
    bookingDescription?: string;
    bookingEntryDescription?: string;
    datetime: string;
    amountInCents: number;
}
