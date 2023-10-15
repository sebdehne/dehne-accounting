import {Button, Container} from "@mui/material";
import {useNavigate, useParams} from "react-router-dom";
import React, {useEffect, useState} from "react";
import {
    MatchedUnbookedBankTransactionMatcher,
    UnbookedBankTransactionMatcher
} from "../../Websocket/types/unbookedTransactions";
import WebsocketClient from "../../Websocket/websocketClient";
import Header from "../Header";
import AddIcon from "@mui/icons-material/Add";

export const TransactionMatchingV2 = () => {
    const {accountId, txId} = useParams();
    const [matchers, setMatchers] = useState<MatchedUnbookedBankTransactionMatcher[]>([]);

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

    const navigate = useNavigate();
    const addUrl = unbookedTransactionId ? '/matcher/' + accountId + '/' + unbookedTransactionId : '/matcher'

    return (<Container maxWidth="xs">

        <Header title={'Book'}/>

        <div style={{display: "flex", flexDirection: "row", justifyContent: "space-between"}}>
            <h4>Available matchers:</h4>
            <Button onClick={() => navigate(addUrl)}><AddIcon/>Add</Button>
        </div>

        <ul>
            {matchers.filter(m => !m.matcher).map(m => (
                <MatcherViewLi key={m.matcher.id} m={m.matcher}/>
            ))}
        </ul>


    </Container>)
}


type MatcherViewProps = {
    m: UnbookedBankTransactionMatcher
}
const MatcherViewLi = ({m}: MatcherViewProps) => {

    return <li>
        {m.name}
    </li>
}


