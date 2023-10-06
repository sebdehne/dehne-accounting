import React from "react";
import {useGlobalState} from "../utils/userstate";
import {amountInCentsToString} from "../utils/formatting";


export type AmountProps = {
    amountInCents: number;
}

export const Amount = ({amountInCents}: AmountProps) => {
    const {userState} = useGlobalState();

    return (<>
        {userState?.locale && amountInCentsToString(amountInCents, userState.locale)}
    </>);
}
