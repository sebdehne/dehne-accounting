import React, {useContext, useEffect, useState} from 'react';
import WebsocketClient from "../Websocket/websocketClient";
import {UserStateV2} from "../Websocket/types/UserStateV2";
import {Realm} from "../Websocket/types/realm";
import {Accounts} from "./accounts";

type ContextType = {
    userStateV2: UserStateV2 | undefined;
    setUserStateV2: (fn: (prev: UserStateV2) => UserStateV2) => Promise<void>;
    realm: Realm | undefined;
    accounts: Accounts;
}

const GlobalStateProviderContext = React.createContext({} as ContextType);

export type UserStateProviderProps = {
    children?: React.ReactNode;
}
export const GlobalStateProvider = ({children,}: UserStateProviderProps) => {
    const [userStateV2, setUserStateV2] = useState<UserStateV2 | undefined>();
    const [realm, setRealm] = useState<Realm>();
    const [accounts, setAccounts] = useState<Accounts>(new Accounts([]));

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
                setUserStateV2(notify.readResponse.userStateV2);
            }
        )
        return () => WebsocketClient.unsubscribe(subId);
    }, [setUserStateV2]);

    useEffect(() => {
        const subId = WebsocketClient.subscribe(
            {type: 'getAllAccounts'},
            notify => {
                setAccounts(new Accounts(notify.readResponse.allAccounts!));
            }
        )
        return () => WebsocketClient.unsubscribe(subId);
    }, [setAccounts]);


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
            setUserStateV2: updateStateV2,
            userStateV2,
            realm,
            accounts
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