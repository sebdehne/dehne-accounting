import {BankAccountTransactionView} from "../../Websocket/types/banktransactions";
import {
    BookingRule,
    BookingRuleType,
    TransactionMatcher,
    TransactionMatcherTargetType
} from "../../Websocket/types/transactionMatcher";
import React, {useCallback, useState} from "react";
import DeleteIcon from "@mui/icons-material/Delete";
import AddIcon from "@mui/icons-material/Add";
import IconButton from "@mui/material/IconButton";
import {removeItemWithSlice} from "../../utils/utils";
import {FormControl, InputLabel, MenuItem, Select, TextField} from "@mui/material";
import {CategorySearchBox, SearchableCategory} from "../CategorySearchBox/CategorySearchBox";
import "./ActionEditor.css";


export type ActionEditorProps = {
    matcher: TransactionMatcher;
    setMatcher: React.Dispatch<React.SetStateAction<TransactionMatcher>>;
}

export const ActionEditor = ({matcher, setMatcher}: ActionEditorProps) => {
    const setRules = useCallback((isCredit: boolean) => (r: BookingRule[]) => {
        setMatcher(prevState => ({
            ...prevState,
            target: {
                ...prevState.target,
                multipleCategoriesBooking: {
                    ...prevState.target.multipleCategoriesBooking,
                    creditRules: isCredit ? r : prevState.target.multipleCategoriesBooking!.creditRules,
                    debitRules: isCredit ? prevState.target.multipleCategoriesBooking!.debitRules : r,
                }
            }
        }))
    }, [matcher]);


    return (<div>

        <FormControl sx={{m: 1, minWidth: 120}}>
            <InputLabel id="demo-simple-select-helper-label">Target type</InputLabel>
            <Select
                labelId="demo-simple-select-helper-label"
                id="demo-simple-select-helper"
                value={matcher.target.type}
                label="Target type"
                onChange={(event, child) => setMatcher(prevState => ({
                    ...prevState,
                    target: {
                        ...prevState.target,
                        type: event.target.value as TransactionMatcherTargetType
                    }
                }))}
            >
                <MenuItem value={'multipleCategoriesBooking'}>multipleCategoriesBooking</MenuItem>
                <MenuItem value={'bankTransfer'}>bankTransfer</MenuItem>
            </Select>
        </FormControl>

        {matcher.target.type === "multipleCategoriesBooking" && <div>
            <BookingRulesEditor title={'credit'} rules={matcher.target.multipleCategoriesBooking?.creditRules ?? []} setRules={setRules(true)}/>
            <BookingRulesEditor title={'debit'} rules={matcher.target.multipleCategoriesBooking?.debitRules ?? []} setRules={setRules(false)}/>
        </div>}
        {matcher.target.type === "bankTransfer" && <div>
            <CategorySearchBox includeIntermediate={true} onSelectedCategoryId={category => setMatcher(prevState => ({
                ...prevState,
                target: {
                    ...prevState.target,
                    transferCategory: category,
                    transferCategoryId: category.category.id
                }
            }))}/>
        </div>}
    </div>);
}

type BookingRulesEditorProps = {
    title: string;
    rules: BookingRule[];
    setRules: (r: BookingRule[]) => void;
}
const BookingRulesEditor = ({title, rules, setRules}: BookingRulesEditorProps) => {

    const [type, setType] = useState<BookingRuleType>("categoryBookingRemaining");
    const [category, setCategory] = useState<SearchableCategory>();
    const [amountInCents, setAmountInCents] = useState('');

    const removeRule = useCallback((index: number) => {
        setRules(removeItemWithSlice(rules, index))
    }, [setRules]);
    const addRule = useCallback(() => setRules([...rules, {
        type,
        category: category!,
        categoryId: category!.category.id,
        amountInCents: parseInt(amountInCents),
    }]), [setRules, type, category, amountInCents]);

    return (<div>
        <h4>{title} rules</h4>

        <ul className="BookingRules">
            {rules.map((r, index) => (<li key={index} className="BookingRule">
                <div className="BookingRuleSummary">
                    <div>Type: {r.type}</div>
                    <div>category: {r.category.category.name}</div>
                    {!!r.amountInCents && <div>Amount: {r.amountInCents}</div>}
                </div>
                <IconButton size="large" onClick={() => removeRule(index)}><DeleteIcon fontSize="inherit"/></IconButton>
            </li>))}
        </ul>

        <div>
            <h5>Add:</h5>
            <FormControl sx={{m: 1, minWidth: 120}}>
                <InputLabel id="demo-simple-select-helper-label">Type</InputLabel>
                <Select
                    labelId="demo-simple-select-helper-label"
                    id="demo-simple-select-helper"
                    value={type}
                    label="Type"
                    onChange={(event, child) => {
                        setType(event.target.value as BookingRuleType);
                        setAmountInCents('');
                    }}
                >
                    <MenuItem value={'categoryBookingRemaining'}>categoryBookingRemaining</MenuItem>
                    <MenuItem value={'categoryBookingFixedAmount'}>categoryBookingFixedAmount</MenuItem>
                </Select>
            </FormControl>
            {type === "categoryBookingFixedAmount" && <FormControl sx={{m: 1, width: '100%'}}>
                <TextField
                    value={amountInCents}
                    label="Amount"
                    onChange={event => setAmountInCents(event.target.value ?? '')}
                />
            </FormControl>}
            <CategorySearchBox includeIntermediate={true} onSelectedCategoryId={category => setCategory(category)}/>
            <IconButton size="large" onClick={() => addRule()}><AddIcon fontSize="inherit"/></IconButton>
        </div>
    </div>)
}