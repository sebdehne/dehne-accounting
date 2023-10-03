import React, {useContext, useEffect, useState} from 'react';
import {formatIso, monthDelta, startOfCurrentMonth} from "./formatting";
import WebsocketClient from "../Websocket/websocketClient";
import JSONObjectMerge from "json-object-merge";

type ContextType = {
    userState: UserStateFrontendState,
    setUserState: (fn: (prev: UserStateFrontendState) => UserStateFrontendState) => Promise<void>
}

const UserStateProviderContext = React.createContext({} as ContextType);

export type UserStateProviderProps = {
    children?: React.ReactNode;
    value?: UserStateFrontendState;
}
export const UserStateProvider = ({children, value = createDefault()}: UserStateProviderProps) => {
    const [userState, setUserState] = useState(value);

    useEffect(() => {
        const subId = WebsocketClient.subscribe({type: 'userState'},
            notify => setUserState(
                createDefault(notify.readResponse.userState!.frontendState)
            )
        )
        return () => WebsocketClient.unsubscribe(subId);
    }, [setUserState]);

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
        <UserStateProviderContext.Provider value={{userState, setUserState: updateState}}>
            {children}
        </UserStateProviderContext.Provider>
    );
};

export const useUserState = () => {
    const context = useContext(UserStateProviderContext);
    if (!context) {
        throw new Error("useGlobalState must be used within a UserStateProviderContext");
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