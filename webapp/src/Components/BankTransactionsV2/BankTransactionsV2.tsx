import React, {useCallback, useEffect, useState} from "react";
import {Container} from "@mui/material";
import Header from "../Header";
import {useNavigate, useParams} from "react-router-dom";
import {BankAccountTransaction} from "../../Websocket/types/banktransactions";
import {useGlobalState} from "../../utils/globalstate";
import WebsocketClient from "../../Websocket/websocketClient";
import {amountInCentsToString} from "../../utils/formatting";
import "./BankTransactionsV2.css"
import CheckIcon from "@mui/icons-material/Check";
import IconButton from "@mui/material/IconButton";
import InputIcon from "@mui/icons-material/Input";
import {PeriodSelectorV2} from "../PeriodSelectors/PeriodSelector";
import {useDialogs} from "../../utils/dialogs";
import {DateViewer} from "../PeriodSelectors/DateViewer";
import DeleteIcon from "@mui/icons-material/Delete";
import dayjs from "dayjs";

export const BankTransactionsV2 = () => {
    const {accountId} = useParams();
    const [transactions, setTransactions] = useState<BankAccountTransaction[]>([]);
    const {accounts} = useGlobalState();


    useEffect(() => {
        if (accountId) {
            let subscribe = WebsocketClient.subscribe(
                {type: "getBankAccountTransactions", accountId: accountId},
                readResponse => setTransactions(readResponse.getBankAccountTransactions!)
            );
            return () => WebsocketClient.unsubscribe(subscribe);
        }

    }, [setTransactions, accountId]);

    const navigate = useNavigate();

    const onImport = () => {
        navigate('/bankaccount_tx/' + accountId + '/import');
    }

    const {showConfirmationDialog} = useDialogs();

    const onDeleteAll = () => {
        showConfirmationDialog({
            onConfirmed: () => WebsocketClient.rpc({type: "deleteAllUnbookedTransactions", "accountId": accountId}),
            content: <p>Are you sure you want to delete all unbooked transactions? This cannot be undone.</p>,
            header: "Delete all unbooked transactions?",
        })
    }

    if (!accounts.hasData()) return null;

    const account = accounts.getById(accountId!);

    return (<Container maxWidth="xs">
        <Header title={account?.name ?? 'Transactions for...'} extraMenuOptions={[
            ['Import transactions', onImport],
            ['Delete all unbooked', onDeleteAll]
        ]}/>

        <PeriodSelectorV2/>

        <ul className="TransactionsV2">
            {transactions.map((transaction, index) => (<li key={index} style={{padding: '0'}}>
                <TransactionView
                    showRightAccountId={accountId}
                    otherAccountName={transaction.bookingReference?.otherAccountId ? accounts.getById(transaction.bookingReference?.otherAccountId)?.name : undefined}
                    balance={transaction.balance}
                    amountInCents={transaction.amountInCents}
                    memo={transaction.memo}
                    datetime={dayjs(transaction.datetime)}
                    unbookedId={transaction.unbookedReference?.unbookedId}
                    bookingId={transaction.bookingReference?.bookingId}
                />
            </li>))}
        </ul>

    </Container>)
}


export type TransactionViewProps = {
    showRightAccountId?: string;
    otherAccountName?: string;
    balance?: number;
    unbookedId: number | undefined;
    bookingId: number | undefined;
    amountInCents: number;
    memo: string | undefined;
    datetime: dayjs.Dayjs;
}
export const TransactionView = ({
                                    showRightAccountId,
                                    otherAccountName,
                                    balance,
                                    unbookedId,
                                    bookingId,
                                    amountInCents,
                                    memo,
                                    datetime
                                }: TransactionViewProps) => {
    const navigate = useNavigate();
    const {showConfirmationDialog} = useDialogs();

    const formatText = useCallback((text: string | undefined) => {
        if (text?.includes("T0000")) return ""
        return text ? " - " + text : "";
    }, []);

    const book = (txId: number) => {
        navigate('/matchers/' + showRightAccountId + '/' + txId);
    }

    const deleteUnbookedTx = (txId: number) => {
        if (showRightAccountId) {
            showConfirmationDialog({
                header: "Delete unbooked transaction?",
                content: "Are you sure? This cannot be undone",
                onConfirmed: () => {
                    WebsocketClient.rpc({
                        type: "deleteUnbookedTransaction",
                        accountId: showRightAccountId,
                        deleteUnbookedBankTransactionId: txId
                    })
                }
            })
        }
    }

    return (<div className="TransactionV2">
        <div className="TransactionLeft" onClick={() => {
            if (bookingId && bookingId !== unbookedId) {
                navigate("/booking/" + bookingId);
            }
        }}>
            <div className="TransactionUp">
                {otherAccountName &&
                    <div>{otherAccountName} {formatText(memo)}</div>}
                {!otherAccountName && <div>{memo}</div>}

                <div>{amountInCentsToString(amountInCents)}</div>
            </div>
            <div className="TransactionDown">
                <DateViewer date={datetime}/>
                {balance && <div>{amountInCentsToString(balance)}</div>}
            </div>
        </div>
        {showRightAccountId && <div className="TransactionRight">
            {!unbookedId &&
                <div style={{color: "lightgreen", width: "40px", display: "flex", justifyContent: "flex-end"}}>
                    <CheckIcon/></div>}
            {unbookedId && <IconButton onClick={() => book(unbookedId)}><InputIcon/></IconButton>}
            {unbookedId && <IconButton onClick={() => deleteUnbookedTx(unbookedId)}><DeleteIcon/></IconButton>}
        </div>}


    </div>)
}
