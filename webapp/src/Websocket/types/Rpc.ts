import {Subscribe, Unsubscribe} from "./Subscription";
import {ExecuteMatcherRequest, TransactionMatcher} from "./transactionMatcher";
import {UserState} from "../../utils/userstate";
import {CategoryDto, MergeCategoriesRequest} from "./categories";
import {BookingView} from "./bookings";
import {UserStateV2} from "./UserStateV2";
import {UnbookedBankTransactionMatcher} from "./unbookedTransactions";

export type RequestType = "subscribe"
    | "unsubscribe"
    | 'importBankTransactions'
    | 'addOrReplaceMatcher'
    | 'deleteMatcher'
    | 'executeMatcher'
    | 'setUserState'
    | 'addOrReplaceBooking'
    | 'removeBooking'
    | 'removeLastBankTransaction'
    | 'addOrReplaceCategory'
    | 'mergeCategories'
    | 'setUserStateV2'
    | 'deleteAllUnbookedTransactions'
    | 'addOrReplaceUnbookedTransactionMatcher'
    | 'removeUnbookedTransactionMatcher'
    ;

export type RpcRequest = {
    type: RequestType;
    subscribe?: Subscribe;
    unsubscribe?: Unsubscribe;

    ledgerId?: string;
    bankAccountId?: string;
    accountId?: string;
    importBankTransactionsRequest?: ImportBankTransactionsRequest;
    addOrReplaceMatcherRequest?: TransactionMatcher;
    executeMatcherRequest?: ExecuteMatcherRequest;
    userState?: UserState;
    deleteMatcherId?: string;
    bookingId?: number;
    addOrReplaceCategory?: CategoryDto;
    mergeCategoriesRequest?: MergeCategoriesRequest;
    addOrReplaceBooking?: BookingView;
    userStateV2?: UserStateV2;
    unbookedBankTransactionMatcher?: UnbookedBankTransactionMatcher;
    removeUnbookedTransactionMatcherId?: string;
}

export type RpcResponse = {
    subscriptionCreated?: boolean;
    subscriptionRemoved?: boolean;

    importBankTransactionsResult?: ImportBankTransactionsResult;
    getMatchCandidatesResult?: TransactionMatcher[];
    error?: string;
}

export type ImportBankTransactionsRequest = {
    accountId: string;
    filename: string;
    dataBase64: string;
    duplicationHandlerType: DuplicationHandlerType;
}

export type ImportBankTransactionsResult = {
    imported: number;
    skipped: number;
}

export type DuplicationHandlerType = 'sameDateAndAmount' | 'sameDateAmountAndDescription'
