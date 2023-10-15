import React, {useContext, useEffect, useState} from 'react';
import {formatIso, monthDelta, startOfCurrentMonth} from "./formatting";
import WebsocketClient from "../Websocket/websocketClient";
import JSONObjectMerge from "json-object-merge";
import {CategoryDto} from "../Websocket/types/categories";
import {buildTree, CategoryTree} from "../Components/CategorySearchBox/CategoryTree";
import {LedgerView} from "../Websocket/types/ledgers";
import {UserStateV2} from "../Websocket/types/UserStateV2";
import {Realm} from "../Websocket/types/realm";
import {AccountDto} from "../Websocket/types/accounts";

type ContextType = {
    userState: UserStateFrontendState | undefined;
    userStateV2: UserStateV2 | undefined;
    setUserState: (fn: (prev: UserStateFrontendState) => UserStateFrontendState) => Promise<void>;
    setUserStateV2: (fn: (prev: UserStateV2) => UserStateV2) => Promise<void>;
    categoriesAsList: CategoryDto[];
    categoriesAsTree: CategoryTree[];
    ledger: LedgerView | undefined;
    realm: Realm | undefined;
    accountsAsList: AccountDto[];
}

const GlobalStateProviderContext = React.createContext({} as ContextType);

export type UserStateProviderProps = {
    children?: React.ReactNode;
}
export const GlobalStateProvider = ({children,}: UserStateProviderProps) => {

    const [userState, setUserState] = useState<UserStateFrontendState | undefined>(undefined);
    const [userStateV2, setUserStateV2] = useState<UserStateV2 | undefined>();
    const [categoriesAsList, setCategoriesAsList] = useState<CategoryDto[]>([]);
    const [categoriesAsTree, setCategoriesAsTree] = useState<CategoryTree[]>([]);
    const [ledger, setLedger] = useState<LedgerView>();
    const [realm, setRealm] = useState<Realm>();
    const [accountsAsList, setAccountsAsList] = useState<AccountDto[]>([]);

    useEffect(() => {
        if (userStateV2?.selectedRealm) {
            const subId = WebsocketClient.subscribe(
                {type: "getAllRealms"},
                notify => {
                    setRealm(notify.readResponse.realms!.find(l => l.id === userStateV2.selectedRealm))
                }
            );
            return () => WebsocketClient.unsubscribe(subId);
        }
    }, [setRealm, userStateV2?.selectedRealm]);

    useEffect(() => {
        const subId = WebsocketClient.subscribe(
            {type: 'getUserState'},
            notify => {
                return setUserStateV2(notify.readResponse.userStateV2);
            }
        )
        return () => WebsocketClient.unsubscribe(subId);
    }, [setUserStateV2]);
    useEffect(() => {
        const subId = WebsocketClient.subscribe(
            {type: 'getAllAccounts'},
            notify => {
                return setAccountsAsList(notify.readResponse.allAccounts!);
            }
        )
        return () => WebsocketClient.unsubscribe(subId);
    }, [setAccountsAsList]);

    const updateState = (fn: (prev: UserStateFrontendState) => UserStateFrontendState) => {
        if (userState) {
            const updated = fn(userState);
            return new Promise<void>(resolve => {
                WebsocketClient.rpc({
                    type: "setUserState",
                    userState: {
                        frontendState: updated
                    }
                }).then(() => resolve())
            });
        }
        return new Promise<void>(resolve => resolve())
    }

    const updateStateV2 = (fn: (prev: UserStateV2) => UserStateV2) => {
        if (userStateV2) {
            const updated = fn(userStateV2);
            return new Promise<void>(resolve => {
                WebsocketClient.rpc({
                    type: "setUserStateV2",
                    userStateV2: updated
                }).then(() => resolve())
            });
        }
        return new Promise<void>(resolve => resolve())
    }


    return (
        <GlobalStateProviderContext.Provider value={{
            userState,
            setUserState: updateState,
            setUserStateV2: updateStateV2,
            categoriesAsList,
            categoriesAsTree,
            ledger,
            userStateV2,
            realm,
            accountsAsList
        }}>
            {children}
        </GlobalStateProviderContext.Provider>
    );
};

export const useGlobalState = () => {
    const context = useContext(GlobalStateProviderContext);
    if (!context) {
        throw new Error("useGlobalState must be used within a GlobalStateProviderContext");
    }
    return context;
};



export type UserState = {
    frontendState: UserStateFrontendState;
}

export type UserStateFrontendState = {
    locale: string;
    bankTransactionsState: BankTransactionsState;
    legderMainState: LegderMainState;
    bookingsState: BookingsState;
    ledgerId?: string;
    bankAccountId?: string;
    transactionId?: number;
}

export type BookingsState = {
    currentPeriod: PeriodWindow;
}
export type LegderMainState = {
    currentPeriod: PeriodWindow;
}

export type BankTransactionsState = {
    currentPeriod: PeriodWindow;
}


export type PeriodWindowType = 'month' | 'betweenDates'
export type PeriodWindow = {
    type: PeriodWindowType;
    startDateTime: string;
    endDateTime: string;
}