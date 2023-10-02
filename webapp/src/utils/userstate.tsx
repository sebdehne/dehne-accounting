import React, {Dispatch, SetStateAction, useContext, useState} from 'react';
import {formatIso, monthDelta, startOfCurrentMonth} from "./formatting";


const UserStateProviderContext = React.createContext(({
    userState: {} as UserStateFrontendState,
    setUserState: {} as Dispatch<SetStateAction<UserStateFrontendState>>,
}));

export type UserStateProviderProps = {
    children?: React.ReactNode;
    value?: UserStateFrontendState;
}
export const UserStateProvider = ({children, value = createDefault()}: UserStateProviderProps) => {
    const [userState, setUserState] = useState(value);

    return (
        <UserStateProviderContext.Provider value={{userState, setUserState}}>
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

const createDefault = (): UserStateFrontendState => {

    return ({
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
        }
    })
}

export type UserStateFrontendState = {
    bankTransactionsState: BankTransactionsState;
    legderMainState: LegderMainState;
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