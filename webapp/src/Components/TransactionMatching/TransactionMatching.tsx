import {Button, Container} from "@mui/material";
import Header from "../Header";
import React, {useEffect, useState} from "react";
import {useParams} from "react-router-dom";
import {TransactionMatcher} from "../../Websocket/types/transactionMatcher";
import WebsocketClient from "../../Websocket/websocketClient";
import './TransactionMatching.css'

export const TransactionMatching = () => {
    const {ledgerId, bankAccountId, transactionId} = useParams();
    const [candidates, setCandidates] = useState<TransactionMatcher[]>([]);



    useEffect(() => {
        if (ledgerId && bankAccountId && transactionId) {
            WebsocketClient.rpc({
                type: "getMatchCandidates",
                getMatchCandidatesRequest: {
                    transactionId: parseInt(transactionId),
                    ledgerId,
                    bankAccountId,
                }
            }).then(resp => setCandidates(resp.getMatchCandidatesResult!));
        }
    }, [ledgerId, bankAccountId, transactionId]);

    return (
        <Container maxWidth="sm" className="App">
            <Header title="Matching.."/>

            {candidates.length > 0 && <div>
                <h4>Candidates found:</h4>
                <ul className="Candidates">
                    {candidates.map(c => (<li key={c.id}>
                        <div className="Candidate">
                            <div>{c.name}</div>
                            <div><Button>Book now</Button></div>
                        </div>
                    </li>))}
                </ul>
            </div>}
        </Container>
    );
}