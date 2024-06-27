import React, {useContext, useEffect, useState} from 'react';
import WebsocketClient from "../Websocket/websocketClient";
import {UserStateV2} from "../Websocket/types/UserStateV2";
import {Accounts} from "./accounts";
import {AccountTree, LocalState} from "../Websocket/types/localstate";
import {User} from "../Websocket/types/User";
import {RealmInfo} from "../Websocket/types/Subscription";
import {GlobalState} from "../Websocket/types/globalstate";
import {emptyAllAllAccounts} from "../Websocket/types/accounts";

type ContextType = {
    userInfo: User | undefined;
    realm: RealmInfo | undefined;
    userStateV2: UserStateV2 | undefined;
    setUserStateV2: (fn: (prev: UserStateV2) => UserStateV2) => Promise<void>;
    accounts: Accounts;
    localState: LocalState;
    setLocalState: (fn: (prev: LocalState) => LocalState) => void;
}

const GlobalStateProviderContext = React.createContext({} as ContextType);

export type UserStateProviderProps = {
    children?: React.ReactNode;
}
export const GlobalStateProvider = ({children,}: UserStateProviderProps) => {
    const [globalState, setGlobalState] = useState<GlobalState>();
    const [localState, setLocalState] = useState<LocalState>({accountTree: new AccountTree()})

    useEffect(() => {
        const subId = WebsocketClient.subscribe(
            {type: "getGlobalState"},
            readResponse => setGlobalState(readResponse.globalState!)
        );
        return () => WebsocketClient.unsubscribe(subId);
    }, [setGlobalState]);

    const setUserStateV2 = (fn: (prev: UserStateV2) => UserStateV2) => {
        if (globalState) {
            const updated = fn(globalState.userStateV2);
            setGlobalState(({
                ...globalState,
                userStateV2: updated
            }));
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
            userInfo: globalState?.user,
            realm: globalState?.globalStateForRealm?.selectedRealmInfo,
            userStateV2: globalState?.userStateV2,
            setUserStateV2,
            accounts: new Accounts(globalState?.globalStateForRealm?.allAccounts ?? emptyAllAllAccounts),
            localState,
            setLocalState,
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

