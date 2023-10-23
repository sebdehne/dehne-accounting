
export type PeriodType = 'month' | 'all';

export type UserStateV2 = {
    selectedRealm?: string;
    rangeFilter?: DateRangeFilter;
    periodType?: PeriodType;
}

export type DateRangeFilter = {
    from: string;
    toExclusive: string;
}

