import {Button, Container, FormControl, InputLabel, MenuItem, Select, TextField} from "@mui/material";
import {useNavigate, useParams} from "react-router-dom";
import React, {useCallback, useEffect, useMemo, useState} from "react";
import Header from "../Header";
import {useGlobalState} from "../../utils/userstate";
import WebsocketClient from "../../Websocket/websocketClient";
import SaveIcon from "@mui/icons-material/Save";
import {AccountSearchBox} from "../AccountSearchBox/AccountSearchBox";
import {
    isAccountPayable,
    isAccountReceivable,
    isBankAccountAsset,
    isBankAccountLiability
} from "../../Websocket/types/accounts";
import {
    AccountAction,
    AccountActionType, AmountBetween,
    ContainsFilter,
    FilterType,
    OrFilter,
    TransferAction, UnbookedTransaction,
    UnbookedTransactionMatcherFilter
} from "../../Websocket/types/unbookedTransactions";
import {BorderedSection} from "../borderedSection/borderedSection";
import AddIcon from "@mui/icons-material/Add";
import {AmountTextField} from "../AmountTextfield/AmountTextfield";
import {Accounts} from "../../utils/accounts";
import {Amount} from "../Amount";
import "./AddOrEditMatcherV2.css"
import DeleteIcon from "@mui/icons-material/Delete";
import {v4 as uuidv4} from "uuid";
import moment from "moment";
import {formatIso} from "../../utils/formatting";
import {removeItemWithSlice} from "../../utils/utils";
import {TransactionView} from "../BankTransactionsV2/BankTransactionsV2";

export const AddOrEditMatcherV2 = () => {
    const {userStateV2, accounts} = useGlobalState();
    const {matcherId, accountId, txId} = useParams();
    const unbookedTransactionId = txId ? parseInt(txId) : undefined;

    const [unbookedTransaction, setUnbookedTransaction] = useState<UnbookedTransaction>();
    const [initialized, setInitialized] = useState(false);
    const [name, setName] = useState<string>('');
    const [actionMemo, setActionMemo] = useState<string>();
    const [actionAccountId, setActionAccountId] = useState<string>();
    const [accountActionPayable, setAccountActionPayable] = useState<AccountAction>({
        "@c": ".AccountAction",
        additionalSplits: {},
        type: "accountsPayable",
        mainAccountId: undefined
    });
    const [accountActionReceivable, setAccountActionReceivable] = useState<AccountAction>({
        "@c": ".AccountAction",
        additionalSplits: {},
        type: "accountsReceivable",
        mainAccountId: undefined
    });
    const [filter, setFilter] = useState<UnbookedTransactionMatcherFilter>({
        '@c': '.ContainsFilter',
        value: "string"
    } as ContainsFilter);

    const navigate = useNavigate();

    useEffect(() => {
        if (matcherId && !initialized) {
            const subId = WebsocketClient.subscribe(
                {type: "getUnbookedBankTransactionMatchers"},
                notify => {
                    let matcher = notify.readResponse.unbookedBankTransactionMatchers!.find(m => m.matcher.id === matcherId)!.matcher;
                    setName(matcher.name);
                    setActionMemo(matcher.actionMemo);
                    setActionAccountId(matcher.actionAccountId);
                    if (matcher.action["@c"] === ".AccountAction") {
                        let matcherAction = matcher.action as AccountAction;
                        if (matcherAction.type === "accountsPayable") {
                            setAccountActionPayable(matcherAction);
                        } else if (matcherAction.type === "accountsReceivable") {
                            setAccountActionReceivable(matcherAction);
                        }
                    }
                    setFilter(matcher.filter);
                    setInitialized(true);
                }
            );
            return () => WebsocketClient.unsubscribe(subId);
        } else if (userStateV2?.selectedRealm && accountId && unbookedTransactionId && !initialized) {
            const subId = WebsocketClient.subscribe(
                {
                    type: "getUnbookedBankTransaction",
                    unbookedBankTransactionReference: {
                        accountId,
                        unbookedTransactionId
                    }
                },
                notify => {
                    const unbookedTransaction = notify.readResponse.unbookedTransaction!;
                    setUnbookedTransaction(unbookedTransaction);
                    setName(unbookedTransaction.memo ?? '');
                    setFilter({
                        '@c': '.ContainsFilter',
                        value: unbookedTransaction.memo
                    } as ContainsFilter)

                    setInitialized(true);
                }
            );
            return () => WebsocketClient.unsubscribe(subId);
        } else if (userStateV2?.selectedRealm && !initialized) {
            setInitialized(true);
        }
    }, [
        userStateV2?.selectedRealm,
        accountId,
        unbookedTransactionId,
        setInitialized,
        setName,
        setActionMemo,
        setActionAccountId,
        setAccountActionPayable,
        setAccountActionReceivable,
        setUnbookedTransaction,
        initialized,
        matcherId,
    ]);

    const type: ActionType = useMemo(() => {
        const selectedAccount = actionAccountId ? accounts.getByIdExpanded(actionAccountId) : undefined;

        if (selectedAccount) {
            if (isAccountPayable(selectedAccount.parentPath)) return 'payable';
            if (isAccountReceivable(selectedAccount.parentPath)) return 'income';
            if (isBankAccountAsset(selectedAccount.parentPath)) return 'transfer';
            if (isBankAccountLiability(selectedAccount.parentPath)) return 'transfer';
        }
        return 'undecided'
    }, [accounts, actionAccountId]);

    const includeList = [
        ['Asset', 'AccountReceivable'],
        ['Asset', 'BankAccountAsset'],
        ['Liability', 'AccountPayable'],
        ['Liability', 'BankAccountLiability'],
    ];

    const isValid = useMemo(() => {

        if (!name) return false;
        if (!actionAccountId) return false;

        if (type === "payable") {
            if (!accountActionPayable.mainAccountId) return false;
        }
        else if (type === "income") {
            if (!accountActionReceivable.mainAccountId) return false;
        }
        else if (type === "undecided") return false;

        return true;
    }, [name, actionAccountId, type, accountActionPayable, accountActionReceivable]);

    const submit = useCallback(() => {
        if (userStateV2?.selectedRealm) {
            WebsocketClient.rpc({
                type: "addOrReplaceUnbookedTransactionMatcher",
                unbookedBankTransactionMatcher: {
                    id: matcherId ?? uuidv4(),
                    name,
                    realmId: userStateV2.selectedRealm,
                    action: type === "transfer" ? TransferAction : type === "payable" ? accountActionPayable : accountActionReceivable,
                    actionAccountId: actionAccountId!,
                    actionMemo,
                    lastUsed: formatIso(moment()),
                    filter
                }
            }).then(() => navigate(-1))
        }

    }, [
        matcherId,
        name,
        userStateV2?.selectedRealm,
        actionAccountId,
        actionMemo,
        type,
        accountActionPayable,
        accountActionReceivable,
        filter,
        navigate
    ]);


    return (<Container maxWidth="xs">
        <Header title={matcherId ? 'Edit matcher' : 'Add new matcher'}/>

        {!initialized && <div>Loading...</div>}

        {unbookedTransaction && <>
            <TransactionView
                amountInCents={unbookedTransaction.amountInCents}
                memo={unbookedTransaction.memo}
                datetime={moment(unbookedTransaction.datetime)}
                unbookedId={unbookedTransaction.id}
                bookingId={undefined}
            />
            <Spacer/>
        </>}

        {initialized && <>


            <TextField
                style={{width: '100%'}}
                label="Name"
                value={name}
                onChange={event => setName(event.target.value)}
            />

            <Spacer/>

            <FilterEditor filter={filter} setFilter={setFilter}/>

            <Spacer/>

            <AccountSearchBox
                onSelectedAccountId={a => setActionAccountId(a)}
                value={actionAccountId}
                includeStartsWithPath={includeList}
            />

            <Spacer/>

            {type === "income" &&
                <AccountActionEditor
                    accounts={accounts}
                    title={"Action for income"}
                    value={accountActionReceivable}
                    setValue={setAccountActionReceivable}
                    type={"accountsReceivable"}
                />}
            {type === "payable" &&
                <AccountActionEditor
                    accounts={accounts}
                    title={"Action for payment"}
                    value={accountActionPayable}
                    setValue={setAccountActionPayable}
                    type={"accountsPayable"}
                />}
            {type === "transfer" && <BorderedSection title={"Selected action"}>This is a transfer</BorderedSection>}

            <Spacer/>

            <TextField
                style={{width: '100%'}}
                label="Memo"
                value={actionMemo}
                onChange={event => setActionMemo(event.target.value)}
            />

            <Spacer/>

            <Button
                disabled={!isValid}
                variant={"contained"}
                onClick={submit}
            >
                <SaveIcon/>
                {matcherId ? 'Update' : 'Create'}
            </Button>
        </>}
    </Container>)
}

type ActionType = 'transfer' | 'payable' | 'income' | 'undecided'

const Spacer = () => {
    return (<div style={{margin: '20px'}}/>)
}


type AccountActionEditorProps = {
    value: AccountAction;
    setValue: React.Dispatch<React.SetStateAction<AccountAction>>;
    title: string;
    type: AccountActionType;
    accounts: Accounts;
}
const AccountActionEditor = ({accounts, value, setValue, title, type}: AccountActionEditorProps) => {
    const [addingSplit, setAddingSplit] = useState(false);
    const [addingSplitAmount, setAddingSplitAmount] = useState(0);
    const [addingSplitAccountId, setAddingSplitAccountId] = useState<string | undefined>();

    const includeList = type === "accountsReceivable"
        ? [['Income']]
        : [['Expense']];

    const addSplit = () => {
        setValue(prevState => ({
            ...prevState,
            additionalSplits: {
                ...prevState.additionalSplits,
                [addingSplitAccountId!]: addingSplitAmount
            }
        }));
        setAddingSplit(false);
    };
    const removeSplit = (accountId: string) => setValue(prevState => {
        let map = prevState.additionalSplits;
        delete map[accountId];
        return ({
            ...prevState,
            additionalSplits: map
        });
    });

    const canAddSplit = !!addingSplitAccountId;

    if (!accounts.hasData()) return null;

    return <BorderedSection title={title}>
        <AccountSearchBox
            onSelectedAccountId={a => setValue(prevState => ({
                ...prevState,
                mainAccountId: a!
            }))}
            value={value.mainAccountId}
            includeStartsWithPath={includeList}
        />

        <Spacer/>

        <p>Extra splits:</p>

        {Object.keys(value.additionalSplits).length > 0 && <ul className="ExtraSplits">
            {Object.entries(value.additionalSplits).map(([accountId, amount]) => (
                <li key={accountId} className="ExtraSplit">
                    <div>{accounts.getById(accountId)!.name}</div>
                    <div><Amount amountInCents={amount}/></div>
                    <Button onClick={() => removeSplit(accountId)}><DeleteIcon/></Button>
                </li>))}
        </ul>
        }

        {addingSplit && <div>
            <AccountSearchBox
                onSelectedAccountId={a => setAddingSplitAccountId(a)}
                value={addingSplitAccountId}
            />
            <Spacer/>
            <AmountTextField initialValue={addingSplitAmount} setValue={newValue => setAddingSplitAmount(newValue)}/>
            <Spacer/>
            <Button disabled={!canAddSplit} onClick={addSplit}><AddIcon/>Add</Button>
        </div>}

        {!addingSplit && <Button onClick={() => setAddingSplit(true)}><AddIcon/>Add extra split</Button>}
        {addingSplit && <Button onClick={() => setAddingSplit(false)}>Cancel</Button>}

    </BorderedSection>
}


type FilterEditorProps = {
    titlePostfix?: string;
    filter: UnbookedTransactionMatcherFilter,
    setFilter: (fn: (prevState: UnbookedTransactionMatcherFilter) => UnbookedTransactionMatcherFilter) => void;
}
const FilterEditor = ({filter, setFilter, titlePostfix = ""}: FilterEditorProps) => {

    const changeType = (type: FilterType) => {
        setFilter(prevState => ({
            ...prevState,
            "@c": type,
            filters: []
        }))
    }

    const valueFilters: FilterType[] = [".ContainsFilter", ".StartsWith", ".EndsWith"]

    const removeFilter = (index: number) => setFilter(prevState => ({
        ...prevState,
        filters: removeItemWithSlice((prevState as OrFilter).filters, index)
    } as OrFilter))
    const updateFilter = (index: number) => (fn: (prevState: UnbookedTransactionMatcherFilter) => UnbookedTransactionMatcherFilter) => {
        setFilter(prevState => {
            let list = (prevState as OrFilter).filters;
            list[index] = fn(list[index]);
            return {
                ...prevState,
                filters: list
            } as OrFilter
        });
    }

    const addFilter = () => setFilter(prevState => {
        let list = (prevState as OrFilter).filters;

        return {
            ...prevState,
            filters: [
                ...list,
                {
                    "@c": ".ContainsFilter",
                    value: ""
                } as ContainsFilter
            ]
        } as OrFilter
    })

    const setValue = (text: string) => setFilter(prevState => ({
        ...prevState,
        value: text
    } as ContainsFilter))
    const setFromAmount = (amount: number) => setFilter(prevState => ({
        ...prevState,
        from: amount
    } as AmountBetween))
    const setToExcludingAmount = (amount: number) => setFilter(prevState => ({
        ...prevState,
        toExcluding: amount
    } as AmountBetween))

    return (<BorderedSection title={"Filter " + titlePostfix}>

        <FormControl fullWidth>
            <InputLabel id="demo-simple-select-label">Filter type</InputLabel>
            <Select
                labelId="demo-simple-select-label"
                id="demo-simple-select"
                value={filter["@c"]}
                label="Filter type"
                onChange={(event) => changeType(event.target.value as FilterType)}
            >
                <MenuItem value={".ContainsFilter"}>ContainsFilter</MenuItem>
                <MenuItem value={".StartsWith"}>StartsWith</MenuItem>
                <MenuItem value={".EndsWith"}>EndsWith</MenuItem>
                <MenuItem value={".AmountBetween"}>AmountBetween</MenuItem>
                <MenuItem value={".OrFilter"}>OrFilter</MenuItem>
                <MenuItem value={".AndFilters"}>AndFilters</MenuItem>
            </Select>
        </FormControl>

        <Spacer/>

        {valueFilters.includes(filter["@c"]) &&
            <TextField
                value={(filter as ContainsFilter).value}
                onChange={event => setValue(event.target.value ?? '')}
                style={{width: '100%'}}
            />}

        {filter["@c"] === ".AmountBetween" && <>
            <AmountTextField initialValue={(filter as AmountBetween).from} setValue={setFromAmount} label={"From"}/>
            <Spacer/>
            <AmountTextField initialValue={(filter as AmountBetween).toExcluding} setValue={setToExcludingAmount} label={"To (excluding)"}/>
        </>}

        {(filter["@c"] === ".OrFilter" || filter["@c"] === ".AndFilters") && <>

            <ul className="Filters">
                {(filter as OrFilter).filters.map((f, index) => (<li key={index}>
                    <FilterEditor filter={f} setFilter={updateFilter(index)}/>
                    <Button onClick={() => removeFilter(index)}><DeleteIcon/>Remove</Button>
                    <Spacer/>
                </li>))}
            </ul>

            <Button onClick={addFilter}><AddIcon/>Add sub filter</Button>
        </>}
    </BorderedSection>)
}