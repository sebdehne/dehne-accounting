import dayjs from "dayjs";
import {useNavigate} from "react-router-dom";
import {useDialogs} from "../../utils/dialogs";
import React, {useCallback} from "react";
import WebsocketClient from "../../Websocket/websocketClient";
import {amountInCentsToString} from "../../utils/formatting";
import {DateViewer} from "../PeriodSelectors/DateViewer";
import CheckIcon from "@mui/icons-material/Check";
import IconButton from "@mui/material/IconButton";
import InputIcon from "@mui/icons-material/Input";
import DeleteIcon from "@mui/icons-material/Delete";

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
