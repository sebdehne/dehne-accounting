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
import {BankAccountTransactionView} from "../../Websocket/types/banktransactions";
import AddIcon from '@mui/icons-material/Add';

export type FilterEditorProps = {
    transaction: BankAccountTransactionView;
    matcher: TransactionMatcher;
    setMatcher: React.Dispatch<React.SetStateAction<TransactionMatcher>>;
}
export const FilterEditor = ({matcher, setMatcher, transaction}: FilterEditorProps) => {

    const [filterType, setFilterType] = useState<TransactionMatcherFilterType>("exact");
    const [pattern, setPattern] = useState(transaction.description);
    const [fromAmount, setFromAmount] = useState('');
    const [toAmount, setToAmount] = useState('');

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

    return (
        <div>

            <ul className="Filters">
                {matcher.filters.map((f, index) => (<li key={index} className="Filter">
                    <div className="FilterSummary">
                        <div>Type: {f.type}</div>
                        {f.type !== "amountBetween" && <div>Pattern: {f.pattern}</div>}
                        {f.type == "amountBetween" && <div>From: {f.fromAmount}</div>}
                        {f.type == "amountBetween" && <div>From: {f.toAmount}</div>}
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
                        onChange={(event, child) => setFilterType(event.target.value as TransactionMatcherFilterType)}
                    >
                        <MenuItem value={'startsWith'}>startsWith</MenuItem>
                        <MenuItem value={'endsWith'}>endsWith</MenuItem>
                        <MenuItem value={'exact'}>exact</MenuItem>
                        <MenuItem value={'contains'}>contains</MenuItem>
                        <MenuItem value={'amountBetween'}>amountBetween</MenuItem>
                    </Select>
                </FormControl>
                {filterType !== "amountBetween" && <FormControl sx={{m: 1, minWidth: 300}}>
                    <TextField value={pattern} label="Pattern"
                               onChange={event => setPattern(event.target.value ?? '')}/>
                </FormControl>}
                {filterType === "amountBetween" && <FormControl sx={{m: 1, minWidth: 300}}>
                    <TextField value={fromAmount} label="From amount"
                               onChange={event => setFromAmount(event.target.value ?? '')}/>
                </FormControl>}
                {filterType === "amountBetween" && <FormControl sx={{m: 1, minWidth: 300}}>
                    <TextField value={toAmount} label="To amount"
                               onChange={event => setToAmount(event.target.value ?? '')}/>
                </FormControl>}
                <FormControl>
                    <IconButton size="large" onClick={() => addFilter(
                        {
                            type: filterType,
                            pattern,
                            toAmount: parseInt(toAmount),
                            fromAmount: parseInt(fromAmount),
                        }
                    )}><AddIcon fontSize="inherit"/></IconButton>
                </FormControl>
            </div>
        </div>
    )
}
