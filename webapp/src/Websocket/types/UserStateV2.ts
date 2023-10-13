
export type periodType = 'month';

export type UserStateV2 = {
    selectedRealm?: string;
    rangeFilter?: DateRangeFilter;
    periodType?: periodType;
}

export type DateRangeFilter = {
    from: string;
    toExclusive: string;
}

