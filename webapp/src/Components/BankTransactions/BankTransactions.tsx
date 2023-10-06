import {useNavigate} from "react-router-dom";
import Header from "../Header";
import {Button, Container, FormControlLabel, Switch} from "@mui/material";
import React, {useCallback, useEffect, useState} from "react";
import WebsocketClient from "../../Websocket/websocketClient";
import {BankAccountView} from "../../Websocket/types/bankaccount";
import {PeriodSelector} from "../PeriodSelectors/PeriodSelector";
import {BankAccountTransactionView} from "../../Websocket/types/banktransactions";
import './BankTransactions.css'
import {useGlobalState} from "../../utils/userstate";
import DeleteIcon from "@mui/icons-material/Delete";
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import {BankTransaction} from "./BankTransaction";

export const BankTransactions = () => {
    const {userState, ledger} = useGlobalState();
    const [bankAccount, setBankAccount] = useState<BankAccountView>()
    const [transactions, setTransactions] = useState<BankAccountTransactionView[]>();
    const [editMode, setEditMode] = useState(false);

    useEffect(() => {
        if (userState?.ledgerId && userState.bankAccountId) {
            const subId = WebsocketClient
                .subscribe(
                    {type: "getBankAccounts", ledgerId: userState.ledgerId},
                    notify => setBankAccount(notify.readResponse.bankAccounts?.find(b => b.id === userState.bankAccountId))
                );

            return () => WebsocketClient.unsubscribe(subId);
        }

    }, [userState]);

    useEffect(() => {
        if (!bankAccount ||!userState?.ledgerId) return () => {
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
    }, [userState, bankAccount]);

    const navigate = useNavigate();



    const removeLastBankTransaction = useCallback(() => {
        if (userState?.ledgerId) {
            WebsocketClient.rpc(
                {
                    type: "removeLastBankTransaction",
                    ledgerId: userState.ledgerId,
                    bankAccountId: userState.bankAccountId,
                }
            )
        }
    }, [userState]);

    return (
        <Container maxWidth="xs" className="App">
            <Header title={bankAccount?.name ?? "Bank account: ..."}/>

            <div className="HeaderLine">
                {editMode && <div>
                    <Button variant={"contained"} onClick={() => navigate('/bankaccount/import')}>
                        <CloudUploadIcon/>&nbsp;Import
                    </Button>
                    <Button variant={"outlined"} onClick={removeLastBankTransaction}>
                        <DeleteIcon/>Delete last TX
                    </Button>
                </div>}
                {!editMode && <div></div>}
                <div><FormControlLabel
                    control={
                        <Switch
                            checked={editMode}
                            onChange={(event, checked) => setEditMode(checked)}
                        />}
                    label="Edit mode"
                    labelPlacement="end"
                /></div>
            </div>

            <PeriodSelector periodLocationInUserState={['bankTransactionsState', 'currentPeriod']}/>

            {userState && ledger && (transactions?.length ?? 0) > 0 && <ul className="Transactions">
                {transactions?.map(t => (<li
                     key={t.id}
                >
                    <BankTransaction t={t} hideButton={false}/>
                </li>))}
            </ul>}

        </Container>
    )
}

