import React from "react";
import {amountInCentsToString} from "../utils/formatting";


export type AmountProps = {
    amountInCents: number;
}

export const Amount = ({amountInCents}: AmountProps) => {
    return (<>
        {amountInCentsToString(amountInCents, 'nb-NO')}
    </>);
}
