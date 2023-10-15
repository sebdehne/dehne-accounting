import React, {useCallback, useEffect, useState} from "react";
import {Container} from "@mui/material";
import Header from "../Header";
import {useNavigate, useParams} from "react-router-dom";
import {BankAccountTransaction} from "../../Websocket/types/banktransactions";
import {useGlobalState} from "../../utils/userstate";
import WebsocketClient from "../../Websocket/websocketClient";
import {amountInCentsToString, formatLocatDayMonth} from "../../utils/formatting";
import moment from "moment";
import "./BankTransactionsV2.css"
import CheckIcon from "@mui/icons-material/Check";
import IconButton from "@mui/material/IconButton";
import InputIcon from "@mui/icons-material/Input";
import {PeriodSelectorV2} from "../PeriodSelectors/PeriodSelector";
import {useDialogs} from "../../utils/dialogs";

export const BankTransactionsV2 = () => {
    const {accountId} = useParams();
    const [transactions, setTransactions] = useState<BankAccountTransaction[]>([]);
    const {accounts} = useGlobalState();

    const account = accounts.getById(accountId!);

    useEffect(() => {
        if (accountId) {
            let subscribe = WebsocketClient.subscribe(
                {type: "getBankAccountTransactions", accountId: accountId},
                notify => setTransactions(notify.readResponse.getBankAccountTransactions!)
            );
            return () => WebsocketClient.unsubscribe(subscribe);
        }

    }, [setTransactions, accountId]);

    const navigate = useNavigate();

    const onImport = () => {
        navigate('/bankaccount/' + accountId + '/import');
    }

    const {showConfirmationDialog} = useDialogs();

    const onDeleteAll = () => {
        showConfirmationDialog({
            onConfirmed: () => WebsocketClient.rpc({type: "deleteAllUnbookedTransactions", "accountId": accountId}),
            content: <p>Are you sure you want to delete all unbooked transactions? This cannot be undone.</p>,
            header: "Delete all unbooked transactions?",
        })
    }

    return (<Container maxWidth="xs">
        <Header title={account?.name ?? 'Transactions for...'} extraMenuOptions={[
            ['Import transactions', onImport],
            ['Delete all unbooked', onDeleteAll]
        ]}/>

        <PeriodSelectorV2/>

        <ul className="TransactionsV2">
            {transactions.map((transaction, index) => (<li key={index} style={{padding: '0'}}>
                <TransactionView
                    showRightAccountId={transaction.unbookedReference ? accountId : undefined}
                    otherAccountName={transaction.bookingReference?.otherAccountId ? accounts.getById(transaction.bookingReference?.otherAccountId).name : undefined}
                    balance={transaction.balance}
                    amountInCents={transaction.amountInCents}
                    memo={transaction.memo}
                    datetime={moment(transaction.datetime)}
                    unbookedId={transaction.unbookedReference?.unbookedId}
                />
            </li>))}
        </ul>

    </Container>)
}


export type TransactionViewProps = {
    showRightAccountId?: string;
    otherAccountName?: string;
    balance?: number;
    unbookedId?: number;
    amountInCents: number;
    memo: string | undefined;
    datetime: moment.Moment;
}
export const TransactionView = ({
                                    showRightAccountId,
                                    otherAccountName,
                                    balance,
                                    unbookedId,
                                    amountInCents,
                                    memo,
                                    datetime
                                }: TransactionViewProps) => {
    const navigate = useNavigate();

    const formatText = useCallback((text: string | undefined) => {
        if (text?.includes("T0000")) return ""
        return text ? " - " + text : "";
    }, []);

    const book = (txId: number) => {
        navigate('/matchers/' + showRightAccountId + '/' + txId);
    }

    return (<div className="TransactionV2">

        <div className="TransactionLeft">
            <div className="TransactionUp">
                {otherAccountName &&
                    <div>{otherAccountName} {formatText(memo)}</div>}
                {!otherAccountName && <div>{memo}</div>}

                <div>{amountInCentsToString(amountInCents)}</div>
            </div>
            <div className="TransactionDown">
                <div>{formatLocatDayMonth(datetime)}</div>
                {balance && <div>{amountInCentsToString(balance)}</div>}
            </div>
        </div>
        {showRightAccountId && <div className="TransactionRight">
            {!unbookedId && <div style={{color: "lightgreen"}}><CheckIcon/></div>}
            {unbookedId && <div style={{width: '24px', height: '30px'}}>
                <IconButton onClick={() => book(unbookedId)}>
                    <InputIcon fontSize="inherit"/>
                </IconButton>
            </div>}
        </div>}


    </div>)
}
