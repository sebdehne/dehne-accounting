

export type OverviewRapportAccount = {
    accountId: string;
    openBalance: number;
    thisPeriod: number;
    closeBalance: number;
    children: OverviewRapportAccount[];
    deepEntrySize: number;
    budget?: number;
}

