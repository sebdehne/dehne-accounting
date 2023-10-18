import {Button, Container, TextField} from "@mui/material";
import {useNavigate, useParams} from "react-router-dom";
import React, {useEffect, useState} from "react";
import {
    MatchedUnbookedBankTransactionMatcher,
    UnbookedBankTransactionMatcher,
    UnbookedTransaction
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
import CheckIcon from "@mui/icons-material/Check";
import AddIcon from "@mui/icons-material/Add";
import {MatcherView} from "./MatcherView";

export const TransactionMatchingV2 = () => {
    const {accountId, txId} = useParams();
    const [matchers, setMatchers] = useState<MatchedUnbookedBankTransactionMatcher[]>([]);
    const [unbookedTransaction, setUnbookedTransaction] = useState<UnbookedTransaction>();
    const [filter, setFilter] = useState('');

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
            notify => setMatchers(
                notify.readResponse.unbookedBankTransactionMatchers!
                    .sort((a, b) => {
                        if (a.matches == b.matches) {
                            return a.matcher.name.localeCompare(b.matcher.name)
                        }
                        return a.matches ? -1 : 1
                    })
            )
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
            header: "Delete matcher: " + m.name + "?",
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

    const {showBookMatcherConfirmation} = useDialogs();

    const bookNow = (matcher: UnbookedBankTransactionMatcher) => {
        if (unbookedTransaction) {
            showBookMatcherConfirmation(
                {
                    matcher,
                    initialMemo: unbookedTransaction.memo,
                    onConfirmed: (memo) => {
                        WebsocketClient.rpc({
                            type: "executeMatcherUnbookedTransactionMatcher",
                            executeMatcherRequest: {
                                accountId: unbookedTransaction.accountId,
                                transactionId: unbookedTransaction.id,
                                matcherId: matcher.id,
                                overrideMemo: memo
                            }
                        }).then(() => navigate('/bankaccount/' + accountId))
                    }
                }
            )
        }

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

        <div style={{display: "flex", flexDirection: "row", justifyContent: "space-between", paddingBottom: '15px'}}>
            <h4>Matchers:</h4>
            <Button variant={"outlined"} onClick={() => navigate(addUrl)}><AddIcon/>Add new</Button>
        </div>


        <TextField
            fullWidth={true}
            label={"Filter"}
            value={filter}
            onChange={event => setFilter(event.target.value ?? '')}
        />

        <ul className="Matchers">
            {matchers.filter(m => !filter || m.matcher.name.toLowerCase().includes(filter.toLowerCase())).map(m => (
                <li key={m.matcher.id}>
                    <MatcherView key={m.matcher.id} matcher={m.matcher} buttons={
                        <div>
                            {m.matches &&
                                <Button variant={"contained"} onClick={() => bookNow(m.matcher)}>Book
                                    now <CheckIcon/></Button>}
                            <IconButton onClick={() => navigate('/matcher/' + m.matcher.id)}><EditIcon/></IconButton>
                            <IconButton onClick={() => deleteMatcher(m.matcher)}><DeleteIcon/></IconButton>
                        </div>
                    }/>

                </li>
            ))}
        </ul>

    </Container>)
}

