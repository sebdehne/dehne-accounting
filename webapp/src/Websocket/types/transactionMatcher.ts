

export type TransactionMatcherFilterType = 'startsWith'
    | 'endsWith'
    | 'exact'
    | 'contains'
    | 'amountBetween'
    | 'income'
    | 'payment'
    | 'ifAccountName'
;

export type TransactionMatcherFilter = {
    type: TransactionMatcherFilterType;
    pattern?: string;
    fromAmount?: number;
    toAmount?: number;
    bankAccountName?: string;
}

export type TransactionMatcherActionType = 'paymentOrIncome'
    | 'bankTransfer'
;

export type TransactionMatcherAction = {
    type: TransactionMatcherActionType;
    transferCategoryId?: string;
    paymentOrIncomeConfig?: PaymentOrIncomeConfig;
}

export type PaymentOrIncomeConfig = {
    mainSide: BookingConfigurationForOneSide;
    negatedSide: BookingConfigurationForOneSide;
}

export type BookingConfigurationForOneSide = {
    categoryToFixedAmountMapping: {[K: string]: number};
    categoryIdRemaining: string;
}


export type TransactionMatcher = {
    id: string;
    ledgerId: string;
    name: string;
    filters: TransactionMatcherFilter[];
    action: TransactionMatcherAction;
    lastUsed: string;
}

export type GetMatchersRequest={
    ledgerId: string;
    testMatchFor?: TestMatchFor;
}

export type TestMatchFor = {
    bankAccountId: string;
    transactionId: number,
}

export type GetMatchersResponse = {
    machers: TransactionMatcher[],
    macherIdsWhichMatched: string[],
}


// V2
export type ExecuteMatcherRequest = {
    accountId: string;
    transactionId: number;
    matcherId: string;
    overrideMemo?: string;
}

