export type UnbookedBankTransactionReference = {
    accountId: string;
    unbookedTransactionId: number;
}

export type FilterType = '.ContainsFilter'
    | '.StartsWith'
    | '.EndsWith'
    | '.AmountBetween'
    | '.OrFilter'
    | '.AndFilters'
    ;

export interface UnbookedTransactionMatcherFilter {
    '@c': FilterType;
}

export interface ContainsFilter extends UnbookedTransactionMatcherFilter {
    '@c': '.ContainsFilter';
    value: string;
}

export interface StartsWith extends UnbookedTransactionMatcherFilter {
    '@c': '.StartsWith';
    value: string;
}

export interface EndsWith extends UnbookedTransactionMatcherFilter {
    '@c': '.EndsWith';
    value: string;
}

export interface AmountBetween extends UnbookedTransactionMatcherFilter {
    '@c': '.AmountBetween';
    from: number;
    toExcluding: number;
}

export interface OrFilter extends UnbookedTransactionMatcherFilter {
    '@c': '.OrFilter';
    filters: UnbookedTransactionMatcherFilter[];
}

export interface AndFilters extends UnbookedTransactionMatcherFilter {
    '@c': '.AndFilters';
    filters: UnbookedTransactionMatcherFilter[];
}

export type MatchedUnbookedBankTransactionMatcher = {
    matches: boolean;
    matcher: UnbookedBankTransactionMatcher;
}

export type UnbookedBankTransactionMatcher = {
    id: string;
    realmId: string;
    name: string;
    filter: UnbookedTransactionMatcherFilter;
    action: UnbookedTransactionMatcherAction;
    actionAccountId: string;
    actionMemo?: string;
    lastUsed: string;
}

export type UnbookedTransactionMatcherActionType = '.TransferAction' | '.AccountAction'

export interface UnbookedTransactionMatcherAction {
    '@c': UnbookedTransactionMatcherActionType;
}

export const TransferAction: UnbookedTransactionMatcherAction  = {
    '@c': '.TransferAction'
}

export interface AccountAction extends UnbookedTransactionMatcherAction {
    '@c': '.AccountAction';
    type: AccountActionType;
    mainAccountId: string | undefined;
    additionalSplits: {[key: string]: number};
}

export type AccountActionType = 'accountsPayable' | 'accountsReceivable';

export type UnbookedTransaction = {
    accountId: string;
    id: number;
    memo?: string;
    datetime: string;
    amountInCents: number;
    otherAccountNumber?: string;
}

