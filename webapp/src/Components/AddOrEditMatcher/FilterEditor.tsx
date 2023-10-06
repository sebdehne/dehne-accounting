import {
    TransactionMatcher,
    TransactionMatcherFilter,
    TransactionMatcherFilterType
} from "../../Websocket/types/transactionMatcher";
import React, {useCallback, useState} from "react";
import {removeItemWithSlice} from "../../utils/utils";
import './FilterEditor.css';
import DeleteIcon from "@mui/icons-material/Delete";
import IconButton from "@mui/material/IconButton";
import {FormControl, InputLabel, MenuItem, Select, TextField} from "@mui/material";
import AddIcon from '@mui/icons-material/Add';
import {AmountTextField} from "../AmountTextfield/AmountTextfield";
import {amountInCentsToString} from "../../utils/formatting";
import {useGlobalState} from "../../utils/userstate";

export type FilterEditorProps = {
    description?: string;
    matcher: TransactionMatcher;
    setMatcher: React.Dispatch<React.SetStateAction<TransactionMatcher>>;
}
export const FilterEditor = ({matcher, setMatcher, description}: FilterEditorProps) => {
    const {userState} = useGlobalState()
    const [filterType, setFilterType] = useState<TransactionMatcherFilterType>("exact");
    const [pattern, setPattern] = useState(description);
    const [fromAmount, setFromAmount] = useState(0);
    const [toAmount, setToAmount] = useState(0);
    const [bankAccountName, setBankAccountName] = useState('');

    const addFilter = useCallback((f: TransactionMatcherFilter) => {
        setMatcher(prevState => ({
            ...prevState,
            filters: [
                ...prevState.filters,
                f
            ]
        }))
    }, [setMatcher]);
    const removeFilter = useCallback((index: number) => {
        setMatcher(prevState => ({
            ...prevState,
            filters: removeItemWithSlice(prevState.filters, index)
        }))
    }, [setMatcher]);

    const needsPattern = (type: TransactionMatcherFilterType) => ['startsWith', 'endsWith', 'exact', 'contains'].includes(type)
    const needsAmounts = (type: TransactionMatcherFilterType) => ['amountBetween'].includes(type)
    const needsBankAccountName = (type: TransactionMatcherFilterType) => ['ifAccountName'].includes(type)

    if (!userState) return null;

    return (
        <div>

            <ul className="Filters">
                {matcher.filters.map((f, index) => (<li key={index} className="Filter">
                    <div className="FilterSummary">
                        <div>Type: {f.type}</div>
                        {needsPattern(f.type) && <div>Pattern: {f.pattern}</div>}
                        {needsAmounts(f.type) && <div>From: {amountInCentsToString(f.fromAmount!, userState.locale)}</div>}
                        {needsAmounts(f.type) && <div>To: {amountInCentsToString(f.toAmount!, userState.locale)}</div>}
                        {needsBankAccountName(f.type) && <div>Bank account: {f.bankAccountName}</div>}
                    </div>
                    <IconButton size="large" onClick={() => removeFilter(index)}>
                        <DeleteIcon fontSize="inherit"/></IconButton>
                </li>))}
            </ul>

            <div>
                <h4>Add:</h4>
                <FormControl sx={{m: 1, minWidth: 120}}>
                    <InputLabel id="demo-simple-select-helper-label">Filter type</InputLabel>
                    <Select
                        labelId="demo-simple-select-helper-label"
                        id="demo-simple-select-helper"
                        value={filterType}
                        label="Filter type"
                        onChange={(event) => setFilterType(event.target.value as TransactionMatcherFilterType)}
                    >
                        <MenuItem value={'startsWith'}>startsWith</MenuItem>
                        <MenuItem value={'endsWith'}>endsWith</MenuItem>
                        <MenuItem value={'exact'}>exact</MenuItem>
                        <MenuItem value={'contains'}>contains</MenuItem>
                        <MenuItem value={'amountBetween'}>amountBetween</MenuItem>
                        <MenuItem value={'income'}>income</MenuItem>
                        <MenuItem value={'payment'}>payment</MenuItem>
                        <MenuItem value={'ifAccountName'}>ifAccountName</MenuItem>
                    </Select>
                </FormControl>
                {needsPattern(filterType) && <FormControl sx={{m: 1, minWidth: 300}}>
                    <TextField value={pattern} label="Pattern"
                               onChange={event => setPattern(event.target.value ?? '')}/>
                </FormControl>}
                {needsAmounts(filterType) &&
                    <AmountTextField label={"From"} initialValue={fromAmount} setValue={newValue => setFromAmount(newValue)}/>
                }
                {needsAmounts(filterType) &&
                    <AmountTextField label={"To"} initialValue={toAmount} setValue={newValue => setToAmount(newValue)}/>
                }
                {needsBankAccountName(filterType) && <FormControl sx={{m: 1, minWidth: 300}}>
                    <TextField value={bankAccountName} label="Bank account name"
                               onChange={event => setBankAccountName(event.target.value ?? '')}/>
                </FormControl>}
                <FormControl>
                    <IconButton size="large" onClick={() => addFilter(
                        {
                            type: filterType,
                            pattern,
                            toAmount: toAmount,
                            fromAmount: fromAmount,
                            bankAccountName,
                        }
                    )}><AddIcon fontSize="inherit"/></IconButton>
                </FormControl>
            </div>
        </div>
    )
}
