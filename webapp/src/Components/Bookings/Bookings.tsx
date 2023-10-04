import Header from "../Header";
import {Container, FormControlLabel, Switch} from "@mui/material";
import React, {useCallback, useEffect, useState} from "react";
import {useGlobalState} from "../../utils/userstate";
import {LedgerView} from "../../Websocket/types/ledgers";
import WebsocketService from "../../Websocket/websocketClient";
import WebsocketClient from "../../Websocket/websocketClient";
import {BookingView} from "../../Websocket/types/bookings";
import {PeriodSelector} from "../PeriodSelectors/PeriodSelector";
import './Bookings.css';
import {formatLocatDayMonth} from "../../utils/formatting";
import moment from "moment";
import {Amount} from "../Amount";
import DeleteIcon from "@mui/icons-material/Delete";
import IconButton from "@mui/material/IconButton";

export const Bookings = () => {
    const {userState} = useGlobalState();
    const [ledger, setLedger] = useState<LedgerView>();
    const [bookings, setBookings] = useState<BookingView[]>([]);
    const [editMode, setEditMode] = useState(false);

    useEffect(() => {
        if (userState.ledgerId) {
            const subId = WebsocketService.subscribe(
                {type: "getLedgers"},
                n => setLedger(n.readResponse.ledgers?.find(l => l.id === userState.ledgerId))
            );

            return () => WebsocketService.unsubscribe(subId);
        }
    }, [setLedger, userState]);

    useEffect(() => {
        if (ledger) {
            const subId = WebsocketClient.subscribe(
                {
                    type: "getBookings",
                    ledgerId: ledger.id,
                    getBookingsRequest: {
                        from: userState.bookingsState.currentPeriod.startDateTime,
                        toExcluding: userState.bookingsState.currentPeriod.endDateTime,
                    }
                },
                notify => setBookings(notify.readResponse.getBookingsResponse!)
            )
            return () => WebsocketService.unsubscribe(subId);
        }
    }, [setBookings, ledger]);

    const deleteBooking = useCallback((bookingId: number) => {
        if (ledger) {
            WebsocketClient.rpc({
                type: "removeBooking",
                ledgerId: ledger.id,
                bookingId
            })
        }
    }, [ledger]);

    return (
        <Container maxWidth="sm" className="App">
            <Header title={"Bookings: " + ledger?.name ?? "..."}/>

            <PeriodSelector periodLocationInUserState={['bookingsState', 'currentPeriod']}/>

            <div className="HeaderLine">
                <div>Found: {bookings.length} bookings</div>
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

            <ul className="Bookings">
                {bookings.map(b => (<li key={b.id} className="Booking">
                    <div className="BookingHeader">
                        <div className="BookingHeaderSummary">
                            <div>{formatLocatDayMonth(moment(b.datetime))}</div>
                            <div style={{marginLeft: '30px', color: 'darkgrey'}}>{b.bookingType}</div>
                            {b.description && <div className="BookingHeaderSummaryDescription"> - {b.description}</div>}
                        </div>
                        {editMode && <IconButton
                            size={"small"}
                            onClick={() => deleteBooking(b.id)}
                        > <DeleteIcon fontSize="inherit"/> </IconButton>
                        }
                    </div>
                    <ul className="BookingRecords">
                        {b.records.map(r => (<li key={r.id} className="BookingRecord">
                            <div className="BookingRecordLeft">
                                <div className="BookingRecordLeftCategory">{r.category.name}</div>
                                {r.description && <div className="BookingRecordLeftDescription"> - {r.description}</div>}
                            </div>
                            <div className="BookingRecordRight">
                                <Amount amountInCents={r.amount}/>
                            </div>
                        </li>))}
                    </ul>
                </li>))}
            </ul>

        </Container>
    );
}