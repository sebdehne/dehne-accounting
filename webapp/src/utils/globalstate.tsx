import React, {useContext, useEffect, useState} from 'react';
import WebsocketClient from "../Websocket/websocketClient";
import {UserStateV2} from "../Websocket/types/UserStateV2";
import {Accounts} from "./accounts";
import {AccountTree, LocalState} from "../Websocket/types/localstate";
import {RealmInfoWithAccessLevel, UserInfo} from "../Websocket/types/User";

type ContextType = {
    userInfo: UserInfo;
    realm: RealmInfoWithAccessLevel | undefined;
    userStateV2: UserStateV2 | undefined;
    setUserStateV2: (fn: (prev: UserStateV2) => UserStateV2) => Promise<void>;
    accounts: Accounts;
    clearAccounts: () => void;
    localState: LocalState;
    setLocalState: (fn: (prev: LocalState) => LocalState) => void;
}

const GlobalStateProviderContext = React.createContext({} as ContextType);

export type UserStateProviderProps = {
    children?: React.ReactNode;
}
export const GlobalStateProvider = ({children,}: UserStateProviderProps) => {
    const [userStateV2, setUserStateV2] = useState<UserStateV2 | undefined>();
    const [accounts, setAccounts] = useState<Accounts>(new Accounts({standardAccounts: [], allAccounts: []}));
    const [localState, setLocalState] = useState<LocalState>({accountTree: new AccountTree()})
    const [userInfo, setUserInfo] = useState<UserInfo>({isAdmin: false, accessibleRealms: []});

    useEffect(() => {
        const subId = WebsocketClient.subscribe(
            {type: "getUserInfo"},
            readResponse => setUserInfo(readResponse.userInfo!)
        );
        return () => WebsocketClient.unsubscribe(subId);
    }, [setUserInfo]);

    useEffect(() => {
        const subId = WebsocketClient.subscribe(
            {type: 'getUserState'},
            readResponse => setUserStateV2(readResponse.userStateV2)
        )
        return () => WebsocketClient.unsubscribe(subId);
    }, [setUserStateV2]);

    useEffect(() => {
        const subId = WebsocketClient.subscribe(
            {type: 'getAllAccounts'},
            readResponse => {
                console.log("UPDATED accounts received")
                setAccounts(new Accounts(readResponse.allAccounts!));
            }
        )
        return () => WebsocketClient.unsubscribe(subId);
    }, [setAccounts]);

    const updateStateV2 = (fn: (prev: UserStateV2) => UserStateV2) => {
        if (userStateV2) {
            const updated = fn(userStateV2);
            setUserStateV2(updated);
            return new Promise<void>(resolve => {
                WebsocketClient.rpc({
                    type: "setUserStateV2",
                    userStateV2: updated
                }).then(() => resolve())
            });
        }
        return new Promise<void>(resolve => resolve())
    }

    const clearAccounts = () => {
        setAccounts(new Accounts({standardAccounts: [], allAccounts: []}))
    }


    return (
        <GlobalStateProviderContext.Provider value={{
            userInfo,
            setUserStateV2: updateStateV2,
            userStateV2,
            realm: userInfo.accessibleRealms.find(ri => ri.id === userStateV2?.selectedRealm),
            accounts,
            localState,
            setLocalState,
            clearAccounts,
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


export type PeriodWindowType = 'month' | 'betweenDates'
export type PeriodWindow = {
    type: PeriodWindowType;
    startDateTime: string;
    endDateTime: string;
}