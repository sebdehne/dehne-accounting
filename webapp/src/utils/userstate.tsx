import React, {useContext, useEffect, useState} from 'react';
import {formatIso, monthDelta, startOfCurrentMonth} from "./formatting";
import WebsocketClient from "../Websocket/websocketClient";
import JSONObjectMerge from "json-object-merge";
import {CategoryDto} from "../Websocket/types/categories";
import {buildTree, CategoryTree} from "../Components/CategorySearchBox/CategoryTree";

type ContextType = {
    userState: UserStateFrontendState | undefined;
    setUserState: (fn: (prev: UserStateFrontendState) => UserStateFrontendState) => Promise<void>;
    categoriesAsList: CategoryDto[];
    categoriesAsTree: CategoryTree[];
}

const GlobalStateProviderContext = React.createContext({} as ContextType);

export type UserStateProviderProps = {
    children?: React.ReactNode;
}
export const GlobalStateProvider = ({children,}: UserStateProviderProps) => {

    const [userState, setUserState] = useState<UserStateFrontendState |undefined>(undefined);
    const [categoriesAsList, setCategoriesAsList] = useState<CategoryDto[]>([]);
    const [categoriesAsTree, setCategoriesAsTree] = useState<CategoryTree[]>([]);

    useEffect(() => {
        const subId = WebsocketClient.subscribe(
            {type: 'userState'},
            notify => {
                return setUserState(
                    createDefault(notify.readResponse.userState!.frontendState)
                );
            }
        )
        return () => WebsocketClient.unsubscribe(subId);
    }, [setUserState]);

    useEffect(() => {
        if (userState?.ledgerId) {
            const subId = WebsocketClient.subscribe(
                {type: "allCategories", ledgerId: userState.ledgerId},
                notify => {
                    setCategoriesAsList(notify.readResponse.categories!);
                    setCategoriesAsTree(buildTree(notify.readResponse.categories!))
                }
            );
            return () => WebsocketClient.unsubscribe(subId);
        }
    }, [setCategoriesAsList, setCategoriesAsTree, userState?.ledgerId]);

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