import React, {useContext, useEffect, useState} from 'react';
import {formatIso, monthDelta, startOfCurrentMonth} from "./formatting";
import WebsocketClient from "../Websocket/websocketClient";
import JSONObjectMerge from "json-object-merge";
import {CategoryView} from "../Websocket/types/categories";
import {buildTree, CategoryTree} from "../Components/CategorySearchBox/CategoryTree";

type ContextType = {
    userState: UserStateFrontendState;
    setUserState: (fn: (prev: UserStateFrontendState) => UserStateFrontendState) => Promise<void>;
    categoriesAsList: CategoryView[];
    categoriesAsTree: CategoryTree[];
}

const GlobalStateProviderContext = React.createContext({} as ContextType);

export type UserStateProviderProps = {
    children?: React.ReactNode;
    userStateInit?: UserStateFrontendState;
    categoriesAsListInit?: CategoryView[];
    categoriesAsTreeInit?: CategoryTree[];
}
export const GlobalStateProvider = ({
                                        children,
                                        userStateInit = createDefault(),
                                        categoriesAsListInit = [],
                                        categoriesAsTreeInit = [],
                                    }: UserStateProviderProps,
) => {
    const [userState, setUserState] = useState(userStateInit);
    const [categoriesAsList, setCategoriesAsList] = useState<CategoryView[]>(categoriesAsListInit);
    const [categoriesAsTree, setCategoriesAsTree] = useState<CategoryTree[]>(categoriesAsTreeInit);

    useEffect(() => {
        const subId = WebsocketClient.subscribe({type: 'userState'},
            notify => setUserState(
                createDefault(notify.readResponse.userState!.frontendState)
            )
        )
        return () => WebsocketClient.unsubscribe(subId);
    }, [setUserState]);
    useEffect(() => {
        const subId = WebsocketClient.subscribe(
            {type: "allCategories"},
            notify => {
                setCategoriesAsList(notify.readResponse.categories!);
                setCategoriesAsTree(buildTree(notify.readResponse.categories!))
            }
        );
        return () => WebsocketClient.unsubscribe(subId);
    }, [setCategoriesAsList, setCategoriesAsTree]);

    const updateState = (fn: (prev: UserStateFrontendState) => UserStateFrontendState) => {
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

    return (
        <GlobalStateProviderContext.Provider value={{
            userState,
            setUserState: updateState,
            categoriesAsList,
            categoriesAsTree
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

const createDefault = (input: any = {}): UserStateFrontendState => {

    const myDefault: UserStateFrontendState = {
        bankTransactionsState: {
            currentPeriod: {
                type: "month",
                startDateTime: formatIso(startOfCurrentMonth()),
                endDateTime: formatIso(monthDelta(startOfCurrentMonth(), 1))
            }
        },
        legderMainState: {
            currentPeriod: {
                type: "month",
                startDateTime: formatIso(startOfCurrentMonth()),
                endDateTime: formatIso(monthDelta(startOfCurrentMonth(), 1))
            }
        },
        bookingsState: {
            currentPeriod: {
                type: "month",
                startDateTime: formatIso(startOfCurrentMonth()),
                endDateTime: formatIso(monthDelta(startOfCurrentMonth(), 1))
            }
        }
    };

    return JSONObjectMerge(myDefault, input) as UserStateFrontendState;
}

export type UserState = {
    frontendState: UserStateFrontendState;
}

export type UserStateFrontendState = {
    bankTransactionsState: BankTransactionsState;
    legderMainState: LegderMainState;
    bookingsState: BookingsState;
    ledgerId?: string;
    bankAccountId?: string;
    transactionId?: number;
    matcherId?: string;
    backUrl?: string;
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