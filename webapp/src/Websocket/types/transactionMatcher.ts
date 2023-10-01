

export type TransactionMatcherFilterType = 'startsWith' | 'endsWith' | 'exact' | 'contains' | 'amountBetween'

export type TransactionMatcherFilter = {
    type: TransactionMatcherFilterType;
    pattern?: string;
    fromAmount?: number;
    toAmount?: number;
}

export type TransactionMatcherTargetType = 'multipleCategoriesBooking' | 'bankTransferReceived' | 'bankTransferSent'

export type TransactionMatcherTarget = {
    type: TransactionMatcherTargetType;
    transferCategoryId?: string;
    multipleCategoriesBooking?: MultipleCategoriesBookingWrapper;
}

export type MultipleCategoriesBookingWrapper = {
    debitRules: BookingRule[];
    creditRules: BookingRule[];
}

export type BookingRuleType = 'categoryBookingRemaining' |  'categoryBookingFixedAmount';

export type BookingRule = {
    type: BookingRuleType;
    categoryId: string;
    amountInCents?: number;
}

export type TransactionMatcher = {
    id: string;
    ledgerId: string;
    name: string;
    filters: TransactionMatcherFilter[];
    target: TransactionMatcherTarget;
}

export type GetMatchCandidatesRequest = {
    ledgerId: string;
    bankAccountId: string;
    transactionId: number;
}

export type ExecuteMatcherRequest = {
    userId: string;
    ledgerId: string;
    bankAccountId: string;
    transactionId: number;
    matcherId: string;
}

