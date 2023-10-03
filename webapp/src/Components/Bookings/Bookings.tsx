import Header from "../Header";
import {Container} from "@mui/material";
import React, {useEffect, useState} from "react";
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

export const Bookings = () => {
    const {userState, setUserState} = useGlobalState();
    const [ledger, setLedger] = useState<LedgerView>();
    const [bookings, setBookings] = useState<BookingView[]>([]);

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

    return (
        <Container maxWidth="sm" className="App">
            <Header
                title={ledger?.name ?? ""}
            />

            <PeriodSelector periodLocationInUserState={['bookingsState', 'currentPeriod']}/>

            <p>Found: {bookings.length} bookings</p>

            <ul className="Bookings">
                {bookings.map(b => (<li key={b.id} className="Booking">
                    <div className="BookingHeader">
                        <div>{formatLocatDayMonth(moment(b.datetime))}</div>
                        <div style={{marginLeft: '30px', color: 'darkgrey'}}>{b.bookingType}</div>
                        <div>{b.description}</div>
                    </div>
                    <ul className="BookingRecords">
                        {b.records.map(r => (<li key={r.id} className="BookingRecord">
                            <div className="BookingRecordLeft">
                                <div>{r.category.name}</div>
                                <div>{r.description}</div>
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