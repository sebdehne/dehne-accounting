

export type OverviewRapportAccount = {
    accountId: string;
    name: string;
    openBalance: number;
    thisPeriod: number;
    closeBalance: number;
    children: OverviewRapportAccount[];
    deepEntrySize: number;
}

