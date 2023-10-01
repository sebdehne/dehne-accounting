import {useNavigate, useParams} from "react-router-dom";
import Header from "../Header";
import {Button, Container} from "@mui/material";
import React, {useEffect, useState} from "react";
import WebsocketClient from "../../Websocket/websocketClient";
import {BankAccountView} from "../../Websocket/types/bankaccount";
import moment from "moment/moment";
import {formatIso, formatLocatDayMonth, monthDelta, startOfCurrentMonth} from "../../utils/formatting";
import {MonthPeriodSelector} from "../PeriodSelectors/MonthPeriodSelector";
import {BankAccountTransactionView} from "../../Websocket/types/banktransactions";
import './BankTransactions.css'
import {Amount} from "../Amount";
import CheckIcon from '@mui/icons-material/Check';
import IconButton from "@mui/material/IconButton";
import ArrowRightIcon from "@mui/icons-material/ArrowRight";

export const BankTransactions = () => {
    const {ledgerId, bankAccountId} = useParams();
    const [bankAccount, setBankAccount] = useState<BankAccountView>()
    const [period, setPeriod] = useState<moment.Moment[]>([
        startOfCurrentMonth(),
        monthDelta(startOfCurrentMonth(), 1)
    ]);
    const [transactions, setTransactions] = useState<BankAccountTransactionView[]>();

    useEffect(() => {
        const subId = WebsocketClient
            .subscribe(
                {type: "getBankAccounts", ledgerId},
                notify => setBankAccount(notify.readResponse.bankAccounts?.find(b => b.id === bankAccountId))
            );

        return () => WebsocketClient.unsubscribe(subId);
    }, [bankAccountId, ledgerId]);

    useEffect(() => {
        if (!bankAccount) return () => {
        };
        const subId = WebsocketClient
            .subscribe(
                {
                    type: "getBankTransactions", ledgerId, bankTransactionsRequest: {
                        from: formatIso(period[0]),
                        toExcluding: formatIso(period[1]),
                        bankAccountId: bankAccount.id
                    }
                },
                notify => setTransactions(notify.readResponse.bankTransactions)
            );

        return () => WebsocketClient.unsubscribe(subId);
    }, [bankAccountId, ledgerId, period]);

    const navigate = useNavigate();

    return (
        <Container maxWidth="sm" className="App">
            <Header
                title={bankAccount?.name ?? "Bank account: ..."}
                backName={"Back"}
                backUrl={'/ledger/' + ledgerId}
            />

            <Button
                onClick={() => navigate('/ledger/' + ledgerId + '/bankaccount/' + bankAccountId + '/import')}>Import</Button>

            <MonthPeriodSelector period={period} setPeriod={setPeriod}/>

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
                                onClick={() => navigate('/ledger/' + ledgerId + '/bankaccount/' + bankAccountId + '/match/' + t.id)}>
                                <ArrowRightIcon fontSize="inherit"/>
                            </IconButton>
                        </div>}
                    </div>
                </li>))}
            </ul>}

        </Container>
    )
}
