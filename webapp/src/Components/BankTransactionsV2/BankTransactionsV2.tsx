import React, {useCallback, useEffect, useState} from "react";
import {Container} from "@mui/material";
import Header from "../Header";
import {useParams} from "react-router-dom";
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

export const BankTransactionsV2 = () => {
    const {accountId} = useParams();
    const [transactions, setTransactions] = useState<BankAccountTransaction[]>([]);
    const {accountsAsList} = useGlobalState();

    const account = accountsAsList.find(a => a.id == accountId)

    useEffect(() => {
        if (accountId) {
            let subscribe = WebsocketClient.subscribe(
                {type: "getBankAccountTransactions", accountId: accountId},
                notify => setTransactions(notify.readResponse.getBankAccountTransactions!)
            );
            return () => WebsocketClient.unsubscribe(subscribe);
        }

    }, [setTransactions, accountId]);

    const getOtherAccount = useCallback(
        (accountId: string) => accountsAsList.find(a => a.id == accountId),
        [accountId, accountsAsList]
    );

    const formatText = useCallback((text?: string) => {
        if (text?.includes("T0000")) return ""
        return text ? " - " + text : "";
    }, []);

    return (<Container maxWidth="xs">
        <Header title={account?.name ?? 'Transactions for...'} extraMenuOptions={[['Import transactions', '/bankaccount/' + accountId + '/import']]}/>

        <PeriodSelectorV2/>

        <ul className="TransactionsV2">
            {transactions.map((transaction, index) => (<li className="TransactionV2" key={index}>

                <div className="TransactionLeft">
                    <div className="TransactionUp">
                        {transaction.bookingReference && <div>{getOtherAccount(transaction.bookingReference.otherAccountId)!.name} {formatText(transaction.memo)}</div>}
                        {!transaction.bookingReference && <div>{transaction.memo}</div>}

                        <div>{amountInCentsToString(transaction.amountInCents)}</div>
                    </div>
                    <div className="TransactionDown">
                        <div>{formatLocatDayMonth(moment(transaction.datetime))}</div>
                        <div>{amountInCentsToString(transaction.balance)}</div>
                    </div>
                </div>
                <div className="TransactionRight">
                    {transaction.bookingReference && <div style={{color: "lightgreen"}}><CheckIcon/></div>}
                    {transaction.unbookedReference && <div style={{width: '24px', height: '30px'}}>
                        <IconButton>
                            <InputIcon fontSize="inherit"/>
                        </IconButton>
                    </div>}
                </div>

            </li>))}
        </ul>

    </Container>)
}

