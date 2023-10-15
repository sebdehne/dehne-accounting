import {Button, Container} from "@mui/material";
import {useNavigate, useParams} from "react-router-dom";
import React, {useEffect, useState} from "react";
import {
    MatchedUnbookedBankTransactionMatcher,
    UnbookedBankTransactionMatcher,
    UnbookedTransaction
} from "../../Websocket/types/unbookedTransactions";
import WebsocketClient from "../../Websocket/websocketClient";
import Header from "../Header";
import AddIcon from "@mui/icons-material/Add";
import {TransactionView} from "../BankTransactionsV2/BankTransactionsV2";
import moment from "moment/moment";

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

        <h4>Matched matchers:</h4>

        <ul className="Matchers">
            {matchers.filter(m => m.matcher).map(m => (
                <li style={{
                    display: 'flex',
                    flexDirection: 'row',
                    justifyContent: "space-between",
                    alignItems: "center",
                    padding: '0'
                }}>
                    <MatcherView key={m.matcher.id} matcher={m.matcher}/>
                    <Button>Book now</Button>
                </li>
            ))}
        </ul>

        <div style={{display: "flex", flexDirection: "row", justifyContent: "space-between"}}>
            <h4>Available matchers:</h4>
            <Button onClick={() => navigate(addUrl)}><AddIcon/>Add</Button>
        </div>

        <ul>
            {matchers.filter(m => !m.matcher).map(m => (
                <MatcherView key={m.matcher.id} matcher={m.matcher}/>
            ))}
        </ul>

    </Container>)
}


type MatcherViewProps = {
    matcher: UnbookedBankTransactionMatcher
}
const MatcherView = ({matcher}: MatcherViewProps) => {

    return <div>
        {matcher.name}
    </div>
}


