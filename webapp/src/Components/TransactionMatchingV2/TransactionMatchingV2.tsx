import {Button, Container} from "@mui/material";
import {useNavigate, useParams} from "react-router-dom";
import React, {useEffect, useState} from "react";
import {
    AccountAction, AmountBetween, AndFilters, ContainsFilter, EndsWith,
    MatchedUnbookedBankTransactionMatcher, OrFilter, StartsWith,
    UnbookedBankTransactionMatcher,
    UnbookedTransaction, UnbookedTransactionMatcherFilter
} from "../../Websocket/types/unbookedTransactions";
import WebsocketClient from "../../Websocket/websocketClient";
import Header from "../Header";
import {TransactionView} from "../BankTransactionsV2/BankTransactionsV2";
import moment from "moment/moment";
import DeleteIcon from "@mui/icons-material/Delete";
import EditIcon from "@mui/icons-material/Edit";
import IconButton from "@mui/material/IconButton";
import {useDialogs} from "../../utils/dialogs";
import "./TransactionMatchingV2.css"
import {useGlobalState} from "../../utils/userstate";
import {BorderedSection} from "../borderedSection/borderedSection";
import CheckIcon from "@mui/icons-material/Check";
import AddIcon from "@mui/icons-material/Add";
import {Amount} from "../Amount";

export const TransactionMatchingV2 = () => {
    const {accountId, txId} = useParams();
    const [matchers, setMatchers] = useState<MatchedUnbookedBankTransactionMatcher[]>([]);
    const [unbookedTransaction, setUnbookedTransaction] = useState<UnbookedTransaction>();

    const unbookedTransactionId = txId ? parseInt(txId) : undefined;

    useEffect(() => {
        WebsocketClient.subscribe(
            {
                type: 'getUnbookedBankTransactionMatchers',
                unbookedBankTransactionReference: accountId && unbookedTransactionId ? {
                    accountId,
                    unbookedTransactionId
                } : undefined
            },
            notify => setMatchers(notify.readResponse.unbookedBankTransactionMatchers!)
        )
    }, [accountId, txId]);

    useEffect(() => {
        if (unbookedTransactionId && accountId) {
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
                }
            );
            return () => WebsocketClient.unsubscribe(subId);
        }
    }, []);

    const navigate = useNavigate();
    const addUrl = unbookedTransactionId ? '/matcher/' + accountId + '/' + unbookedTransactionId : '/matcher'

    const {showConfirmationDialog} = useDialogs();

    const deleteMatcher = (m: UnbookedBankTransactionMatcher) => {
        showConfirmationDialog({
            header: "Delete matcher" + m.name + "?",
            confirmButtonText: "Delete",
            onConfirmed: () => {
                WebsocketClient.rpc({
                    type: "removeUnbookedTransactionMatcher",
                    removeUnbookedTransactionMatcherId: m.id
                })
            },
            content: "This cannot be undone"
        })
    }

    return (<Container maxWidth="xs">

        <Header title={'Book'}/>

        {unbookedTransaction && <>
            <TransactionView
                amountInCents={unbookedTransaction.amountInCents}
                memo={unbookedTransaction.memo}
                datetime={moment(unbookedTransaction.datetime)}
                unbookedId={unbookedTransaction.id}
            />
        </>}

        <div style={{display: "flex", flexDirection: "row", justifyContent: "space-between"}}>
            <h4>Matchers:</h4>
            <Button variant={"outlined"} onClick={() => navigate(addUrl)}><AddIcon/>Add new</Button>
        </div>

        <ul className="Matchers">
            {matchers.filter(m => m.matcher).map(m => (
                <li key={m.matcher.id}>
                    <MatcherView key={m.matcher.id} matcher={m.matcher} buttons={
                        <div>
                            {m.matches && <Button variant={"contained"}>Book now <CheckIcon/></Button>}
                            <IconButton onClick={() => navigate('/matcher/' + m.matcher.id)}><EditIcon/></IconButton>
                            <IconButton onClick={() => deleteMatcher(m.matcher)}><DeleteIcon/></IconButton>
                        </div>
                    }/>

                </li>
            ))}
        </ul>

    </Container>)
}

type MatcherViewProps = {
    matcher: UnbookedBankTransactionMatcher,
    buttons: React.ReactNode
}
const MatcherView = ({matcher, buttons}: MatcherViewProps) => {
    const {accounts} = useGlobalState()

    if (!accounts.hasData()) return null;

    const account = accounts.getById(matcher.actionAccountId)

    const type = matcher.action["@c"] === ".TransferAction"
        ? "Transfer"
        : (matcher.action as AccountAction).type === "accountsPayable"
            ? "Payment"
            : "Income";

    const mainAccount = matcher.action["@c"] === ".AccountAction"
        ? accounts.getById((matcher.action as AccountAction).mainAccountId!)
        : undefined;


    return <div className="MatcherView">
        <div className="MatcherViewSummary">
            <div className="MatcherViewSummaryName">{matcher.name}</div>
            {buttons}
        </div>
        <BorderedSection title={"Filter:"}>
            <FilterView filter={matcher.filter}/>
        </BorderedSection>
        <BorderedSection title={"Action: " + type}>
            <div className="MatcherViewAccount">
                <div className="MatcherViewAccountType">
                    {type == "Transfer" && "To: "}
                    {type == "Payment" && "Pay to: "}
                    {type == "Income" && "From: "}
                </div>
                <div className="MatcherViewAccountAccountName">
                    {account.name}
                </div>
            </div>
            {mainAccount && <div>
                {accounts.generateParentsString(mainAccount.id)} {'->'} {mainAccount.name}
            </div>}
        </BorderedSection>

    </div>
}


type FilterViewProps = {
    filter: UnbookedTransactionMatcherFilter
}
const FilterView = ({filter}: FilterViewProps) => {
    return <div>
        {filter["@c"] === ".ContainsFilter" && <div>Contains: '{(filter as ContainsFilter).value}'</div>}
        {filter["@c"] === ".StartsWith" && <div>Starts with: '{(filter as StartsWith).value}'</div>}
        {filter["@c"] === ".EndsWith" && <div>Ends with: '{(filter as EndsWith).value}'</div>}
        {filter["@c"] === ".AmountBetween" && <div>Amount between <Amount amountInCents={(filter as AmountBetween).from}/> and <Amount amountInCents={(filter as AmountBetween).toExcluding}/></div>}
        {filter["@c"] === ".OrFilter" && <div>Any of: {(filter as OrFilter).filters.map((f,i) => <FilterView key={i} filter={f}/>)}</div>}
        {filter["@c"] === ".AndFilters" && <div>All of: {(filter as AndFilters).filters.map((f,i) => <FilterView key={i} filter={f}/>)}</div>}
    </div>
}

