import {SearchableCategory} from "../../Components/CategorySearchBox/CategorySearchBox";


export type TransactionMatcherFilterType = 'startsWith'
    | 'endsWith'
    | 'exact'
    | 'contains'
    | 'amountBetween'
    | 'deposit'
    | 'withdrawal'
    | 'ifAccountName'
;

export type TransactionMatcherFilter = {
    type: TransactionMatcherFilterType;
    pattern?: string;
    fromAmount?: number;
    toAmount?: number;
    bankAccountName?: string;
}

export type TransactionMatcherTargetType = 'multipleCategoriesBooking'
    | 'bankTransfer'
;

export type TransactionMatcherTarget = {
    type: TransactionMatcherTargetType;
    transferCategoryId?: string;
    multipleCategoriesBooking?: MultipleCategoriesBookingWrapper;

    // internal field
    transferCategory?: SearchableCategory;
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

    // internal field
    category: SearchableCategory;
}

export type TransactionMatcher = {
    id: string;
    ledgerId: string;
    name: string;
    filters: TransactionMatcherFilter[];
    target: TransactionMatcherTarget;
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


export type ExecuteMatcherRequest = {
    ledgerId: string;
    bankAccountId: string;
    transactionId: number;
    matcherId: string;
    memoText?: string;
}

