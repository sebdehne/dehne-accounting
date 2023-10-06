import {amountInCentsToString, formatLocatDayMonth} from "../../utils/formatting";
import moment from "moment";
import CheckIcon from "@mui/icons-material/Check";
import IconButton from "@mui/material/IconButton";
import InputIcon from "@mui/icons-material/Input";
import React from "react";
import "./BankTransaction.css"
import {BankAccountTransactionView} from "../../Websocket/types/banktransactions";
import {useGlobalState} from "../../utils/userstate";
import {useNavigate} from "react-router-dom";

export type BankTransactionProps = {
    t: BankAccountTransactionView,
    hideButton?: boolean
}
export const BankTransaction = ({t, hideButton = true}: BankTransactionProps) => {
    const {userState, setUserState} = useGlobalState();

    const navigate = useNavigate();

    if (!userState) return null;

    const bookTransaction = (transactionId: number) => {
        setUserState(prev => ({
            ...prev,
            transactionId
        })).then(() => navigate('/book/transaction'))
    }

    return (<div className="Transaction">

        <div className="TransactionLeft">
            <div className="TransactionUp">
                <div>{t.description}</div>
                <div>{amountInCentsToString(t.amount, userState.locale)}</div>
            </div>
            <div className="TransactionDown">
                <div>{formatLocatDayMonth(moment(t.datetime))}</div>
                <div>{amountInCentsToString(t.balance, userState.locale)}</div>
            </div>
        </div>
        {!hideButton && <div className="TransactionRight">
            {t.matched && <div style={{color: "lightgreen"}}><CheckIcon/></div>}
            {!t.matched && <div style={{width: '24px', height: '30px'}}>
                <IconButton
                    onClick={() => bookTransaction(t.id)}>
                    <InputIcon fontSize="inherit"/>
                </IconButton>
            </div>}
        </div>}

    </div>)
}