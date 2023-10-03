import {useNavigate} from "react-router-dom";
import Header from "../Header";
import {Button, Container} from "@mui/material";
import React, {useCallback, useEffect, useState} from "react";
import WebsocketClient from "../../Websocket/websocketClient";
import {BankAccountView} from "../../Websocket/types/bankaccount";
import moment from "moment/moment";
import {formatLocatDayMonth} from "../../utils/formatting";
import {PeriodSelector} from "../PeriodSelectors/PeriodSelector";
import {BankAccountTransactionView} from "../../Websocket/types/banktransactions";
import './BankTransactions.css'
import {Amount} from "../Amount";
import CheckIcon from '@mui/icons-material/Check';
import IconButton from "@mui/material/IconButton";
import ArrowRightIcon from "@mui/icons-material/ArrowRight";
import {useGlobalState} from "../../utils/userstate";

export const BankTransactions = () => {
    const {userState, setUserState} = useGlobalState();
    const [bankAccount, setBankAccount] = useState<BankAccountView>()
    const [transactions, setTransactions] = useState<BankAccountTransactionView[]>();

    useEffect(() => {
        if (userState.ledgerId && userState.bankAccountId) {
            const subId = WebsocketClient
                .subscribe(
                    {type: "getBankAccounts", ledgerId: userState.ledgerId},
                    notify => setBankAccount(notify.readResponse.bankAccounts?.find(b => b.id === userState.bankAccountId))
                );

            return () => WebsocketClient.unsubscribe(subId);
        }

    }, [userState]);

    useEffect(() => {
        if (!bankAccount) return () => {
        };

        const subId = WebsocketClient
            .subscribe(
                {
                    type: "getBankTransactions",
                    ledgerId: userState.ledgerId,
                    bankTransactionsRequest: {
                        from: userState.bankTransactionsState.currentPeriod.startDateTime,
                        toExcluding: userState.bankTransactionsState.currentPeriod.endDateTime,
                        bankAccountId: bankAccount.id
                    }
                },
                notify => setTransactions(notify.readResponse.bankTransactions)
            );

        return () => WebsocketClient.unsubscribe(subId);
    }, [userState, userState, bankAccount]);

    const navigate = useNavigate();

    const bookTransaction = (transactionId: number) => {
        setUserState(prev => ({
            ...prev,
            transactionId
        })).then(() => navigate('/book/transaction'))
    }

    const removeLastBankTransaction = useCallback(() => {
        WebsocketClient.rpc(
            {
                type: "removeLastBankTransaction",
                ledgerId: userState.ledgerId,
                bankAccountId: userState.bankAccountId,
            }
        )
    }, []);

    return (
        <Container maxWidth="sm" className="App">
            <Header title={bankAccount?.name ?? "Bank account: ..."}/>

            <Button onClick={() => navigate('/bankaccount/import')}>Import</Button>
            <Button onClick={removeLastBankTransaction}>Delete last TX</Button>

            <PeriodSelector periodLocationInUserState={['bankTransactionsState', 'currentPeriod']}/>

            {(transactions?.length ?? 0) > 0 && <ul className="Transactions">
                {transactions?.map(t => (<li className="Transaction" key={t.id}>
                    <div className="TransactionSummary">
                        <div style={{
                            marginRight: '10px',
                            color: '#a2a2a2',
                            width: '70px'
                        }}>{formatLocatDayMonth(moment(t.datetime))}</div>
                        <div>{t.description}</div>
                    </div>
                    <div className="TransactionAmounts">
                        <Amount amountInCents={t.amount}/>
                        <div style={{color: '#a2a2a2', marginLeft: '8px'}}><Amount amountInCents={t.balance}/></div>
                        {t.matched && <div style={{color: "lightgreen"}}><CheckIcon/></div>}
                        {!t.matched && <div style={{width: '24px', height: '30px'}}>
                            <IconButton
                                onClick={() => bookTransaction(t.id)}>
                                <ArrowRightIcon fontSize="inherit"/>
                            </IconButton>
                        </div>}
                    </div>
                </li>))}
            </ul>}

        </Container>
    )
}
