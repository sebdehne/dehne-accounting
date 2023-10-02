import {Button, Container, FormControl, TextField} from "@mui/material";
import Header from "../Header";
import React, {useEffect, useState} from "react";
import {useNavigate} from "react-router-dom";
import {TransactionMatcher} from "../../Websocket/types/transactionMatcher";
import WebsocketClient from "../../Websocket/websocketClient";
import './BookTransaction.css'
import {BankAccountTransactionView} from "../../Websocket/types/banktransactions";
import {Amount} from "../Amount";
import {formatLocatDayMonth} from "../../utils/formatting";
import moment from "moment";
import DeleteIcon from "@mui/icons-material/Delete";
import AddIcon from "@mui/icons-material/Add";
import EditIcon from "@mui/icons-material/Edit";
import IconButton from "@mui/material/IconButton";
import {useUserState} from "../../utils/userstate";

export const BookTransaction = () => {
    const {userState} = useUserState();
    const [transaction, setTransaction] = useState<BankAccountTransactionView>();

    useEffect(() => {
        if (userState.ledgerId && userState.bankAccountId && userState.transactionId) {
            const subId = WebsocketClient.subscribe(
                {
                    type: "getBankTransaction",
                    bankTransactionRequest: {
                        transactionId: userState.transactionId,
                        ledgerId: userState.ledgerId,
                        bankAccountId: userState.bankAccountId,
                    }
                },
                notify => setTransaction(notify.readResponse.bankTransaction)
            )

            return () => WebsocketClient.unsubscribe(subId);
        }
    }, [userState]);

    const navigate = useNavigate();


    return (
        <Container maxWidth="sm" className="App">
            <Header
                title="Matching"
                backUrl={'/bankaccount'}
                backName={"Back"}
            />

            {transaction && <div>
                <div>{formatLocatDayMonth(moment(transaction.datetime))}</div>
                <div><Amount amountInCents={transaction.amount}/></div>
                <div>{transaction.description}</div>
            </div>}


            {transaction &&
                <MatchersSelector
                    transactionId={transaction.id}
                    ledgerId={userState.ledgerId!}
                    bankAccountId={userState.bankAccountId!}
                    onDone={() => navigate('/bankaccount')}
                />
            }

        </Container>
    );
}


type MatchersSelectorProps = {
    ledgerId: string;
    bankAccountId: string;
    transactionId: number;
    onDone: () => void;
}
const MatchersSelector = ({
                              ledgerId,
                              bankAccountId,
                              transactionId,
                              onDone
                          }: MatchersSelectorProps) => {

    const [allMatchers, setAllMatchers] = useState<TransactionMatcher[]>([]);
    const [macherIdsWhichMatched, setMacherIdsWhichMatched] = useState<string[]>([]);
    const [memoText, setMemoText] = useState('');
    const [allMatchersFilter, setAllMatchersFilter] = useState('');
    const {userState, setUserState} = useUserState();

    const candidates = (all: TransactionMatcher[], macherIdsWhichMatched: string[]) => all.filter(m => macherIdsWhichMatched.includes(m.id))

    useEffect(() => {
        const subId = WebsocketClient.subscribe(
            {
                type: "getMatchers", getMatchersRequest: {
                    ledgerId,
                    testMatchFor: {
                        transactionId,
                        bankAccountId
                    }
                }
            },
            notify => {
                let matchersResponse = notify.readResponse.getMatchersResponse!;
                setAllMatchers(matchersResponse.machers);
                setMacherIdsWhichMatched(matchersResponse.macherIdsWhichMatched);
            }
        )

        return () => WebsocketClient.unsubscribe(subId);

    }, [ledgerId, bankAccountId, transactionId, setAllMatchers, setMacherIdsWhichMatched]);

    const bookNow = (c: TransactionMatcher) => {
        WebsocketClient.rpc({
            type: "executeMatcher",
            executeMatcherRequest: {
                matcherId: c.id,
                transactionId,
                ledgerId,
                bankAccountId,
                memoText,
            }
        }).then(onDone)
    }

    let navigate = useNavigate();

    const editMatcher = (matcherId: string) => {
        setUserState(prev => ({
            ...prev,
            matcherId,
            backUrl: '/book/transaction'
        })).then(() => navigate('/matcher'))
    }

    const addNewMatcher = () => {
        setUserState(prev => ({
            ...prev,
            matcherId: undefined,
            backUrl: '/book/transaction'
        })).then(() => navigate('/matcher'));
    }

    const deleteMatcher = (id: string) => {
        WebsocketClient.rpc({type: "deleteMatcher", ledgerId, deleteMatcherId: id})
    }

    const matcherThatMatch = candidates(allMatchers, macherIdsWhichMatched);
    return (
        <>
            {matcherThatMatch.length > 0 && <div>
                <h3>Matches found</h3>

                <FormControl sx={{m: 1, width: '100%'}}>
                    <TextField
                        value={memoText}
                        label="Memo"
                        onChange={event => setMemoText(event.target.value ?? '')}
                    />
                </FormControl>

                <ul className="Candidates">
                    {matcherThatMatch.map(c => (<li key={c.id}>
                        <div className="Candidate">
                            <div>{c.name}</div>
                            <div><Button onClick={() => bookNow(c)}>Book now</Button></div>
                        </div>
                    </li>))}
                </ul>
            </div>}


            <h3>Available matchers</h3>
            <div className="FilterLine">
                <FormControl sx={{m: 1, width: '100%'}}>
                    <TextField
                        value={allMatchersFilter}
                        label="Filter"
                        onChange={event => setAllMatchersFilter(event.target.value ?? '')}
                    />
                </FormControl>
                <IconButton size="large" onClick={addNewMatcher}><AddIcon fontSize="inherit"/></IconButton>
            </div>

            <ul className="AllMatchers">
                {allMatchers
                    .filter(m => !allMatchers || m.name.toLowerCase().includes(allMatchersFilter))
                    .map(m => (<li key={m.id}>
                        <div className="Matcher">
                            <div>{m.name}</div>
                            <div>
                                <IconButton size="large" onClick={() => editMatcher(m.id)}><EditIcon
                                    fontSize="inherit"/></IconButton>
                                <IconButton size="large" onClick={() => deleteMatcher(m.id)}><DeleteIcon
                                    fontSize="inherit"/></IconButton>
                            </div>
                        </div>
                    </li>))}
            </ul>


        </>
    );
}