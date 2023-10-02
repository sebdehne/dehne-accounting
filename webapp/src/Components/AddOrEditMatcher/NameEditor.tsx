import {BankAccountTransactionView} from "../../Websocket/types/banktransactions";
import {TransactionMatcher} from "../../Websocket/types/transactionMatcher";
import React, {useEffect, useState} from "react";
import {TextField} from "@mui/material";


export type NameEditorProps = {
    matcher: TransactionMatcher;
    setMatcher: React.Dispatch<React.SetStateAction<TransactionMatcher>>;
}
export const NameEditor = ({matcher, setMatcher}: NameEditorProps) => {
    useEffect(() => {
        let initialName = "";
        if (matcher.target.type === "multipleCategoriesBooking") {
            const firstRule = matcher.target.multipleCategoriesBooking!.creditRules[0];
            initialName = firstRule.category.category.name
        }

        setMatcher(({
            ...matcher,
            name: initialName
        }))

    }, []);

    return (<div>
        <TextField
            label="Name"
            value={matcher.name}
            onChange={event => setMatcher(({
                ...matcher,
                name: event.target.value ?? ''
            }))}
        />
    </div>)
}