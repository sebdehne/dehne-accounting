import {Container} from "@mui/material";
import Header from "../Header";
import React, {useEffect, useState} from "react";
import "./BookingForAccountViewer.css";
import {PeriodSelectorV2} from "../PeriodSelectors/PeriodSelector";
import {Booking, BookingEntry} from "../../Websocket/types/bookings";
import WebsocketClient from "../../Websocket/websocketClient";
import {useGlobalState} from "../../utils/userstate";
import {formatLocalDayMonth} from "../../utils/formatting";
import moment from "moment";
import {Amount} from "../Amount";
import {useNavigate, useParams} from "react-router-dom";
import {AccountDto} from "../../Websocket/types/accounts";

export const BookingForAccountViewer = () => {
    const [bookings, setBookings] = useState<Booking[]>([]);
    const {accountId} = useParams();
    const {accounts} = useGlobalState();

    useEffect(() => {
        if (accountId) {
            const sub = WebsocketClient.subscribe(
                {type: "getBookings", accountId},
                notify => setBookings(notify.readResponse.bookings!)
            );
            return () => WebsocketClient.unsubscribe(sub);
        }

    }, [setBookings, accountId]);

    const thisAccount = accountId ? accounts.getById(accountId) : undefined;
    if (!thisAccount) return null;

    return (<Container maxWidth="xs" className="App">
        <Header
            title={accountId ? accounts.getById(accountId).name : ''}
            subTitle={accountId ? accounts.generateParentsString(accountId) : ''}
        />

        <PeriodSelectorV2/>

        {accountId && <ul className="Bookings">
            {bookings.map(b => (
                <BookingViewer key={b.id} booking={b} entry={b.entries.find(e => e.accountId === accountId)}/>))}
        </ul>}

    </Container>)
}


type BookingViewerProps = {
    booking: Booking;
    entry?: BookingEntry
}
const BookingViewer = ({booking, entry}: BookingViewerProps) => {
    const {accounts} = useGlobalState();
    const navigate = useNavigate();

    if (!entry) return null;

    let otherEntries = booking.entries.filter(e => e.id !== entry.id);
    return (<li className="BookingEntry">
        <div className="BookingEntrySummary">
            <div style={{display: "flex", flexDirection: "row"}}>
                <div style={{marginRight: '10px'}}>{formatLocalDayMonth(moment(booking.datetime))}</div>
                <div>{booking.description ?? entry.description}</div>
            </div>
            <div style={{fontSize: "larger", fontWeight: "bold"}}><Amount amountInCents={entry.amountInCents}/></div>
        </div>
        <ul className="OtherEntries">
            {otherEntries.map(e => (<li key={e.id} className="OtherEntry">
                <div onClick={() => navigate('/bookings/' + e.accountId)}>{accounts.generateParentsString(e.accountId)} - {accounts.getById(e.accountId).name}</div>
                {otherEntries.length > 1 && <Amount amountInCents={e.amountInCents}/>}
            </li>))}
        </ul>
    </li>)
}


