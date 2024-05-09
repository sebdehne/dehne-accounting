
export type PeriodType = 'month' | 'all';

export type UserStateV2 = {
    selectedRealm?: string;
    rangeFilter?: DateRangeFilter;
    periodType?: PeriodType;
    frontendState?: FrontendState,
}

export type DateRangeFilter = {
    from: string;
    toExclusive: string;
}

export type FrontendState = {
    hideBookedBankTransactions?: boolean;
}
