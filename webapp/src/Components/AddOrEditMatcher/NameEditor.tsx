import {TransactionMatcher} from "../../Websocket/types/transactionMatcher";
import React, {useEffect} from "react";
import {TextField} from "@mui/material";
import {useGlobalState} from "../../utils/userstate";


export type NameEditorProps = {
    matcher: TransactionMatcher;
    setMatcher: React.Dispatch<React.SetStateAction<TransactionMatcher>>;
    editMode: boolean;
}
export const NameEditor = ({matcher, setMatcher, editMode}: NameEditorProps) => {
    const {categoriesAsList} = useGlobalState();

    useEffect(() => {
        if (!editMode) {
            let initialName = "";
            if (matcher.action.type === "paymentOrIncome") {
                const firstRule = matcher.action.paymentOrIncomeConfig!;
                initialName = categoriesAsList.find(c => c.id === firstRule.mainSide.categoryIdRemaining)?.name ?? ''
            }

            setMatcher(({
                ...matcher,
                name: initialName
            }))
        }
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