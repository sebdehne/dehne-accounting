import {Container} from "@mui/material";
import Header from "../Header";
import React, {useEffect, useState} from "react";
import "./BookingsViewer.css";
import {PeriodSelectorV2} from "../PeriodSelectors/PeriodSelector";
import {Booking, BookingEntry} from "../../Websocket/types/bookings";
import WebsocketClient from "../../Websocket/websocketClient";
import {useGlobalState} from "../../utils/userstate";
import {formatLocalDayMonth} from "../../utils/formatting";
import moment from "moment";
import {Amount} from "../Amount";
import {useParams} from "react-router-dom";

export const BookingsViewer = () => {
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

    return (<Container maxWidth="xs" className="App">
        <Header title={"Bookings"}/>

        <PeriodSelectorV2/>

        <ul className="Bookings">
            {bookings.map(b => (<BookingViewer key={b.id} booking={b}/>))}
        </ul>
    </Container>)
}


type BookingViewerProps = {
    booking: Booking;
}
const BookingViewer = ({booking}: BookingViewerProps) => {

    return (<li className="Booking">
        <div>{formatLocalDayMonth(moment(booking.datetime))}</div>
        <ul className="BookingEntries">
            {booking.entries.map(e => (<BookingEntryViewer key={e.id} entry={e}/>))}
        </ul>
    </li>)
}


type BookingEntryViewerProps = {
    entry: BookingEntry
}
const BookingEntryViewer = ({entry}: BookingEntryViewerProps) => {
    const {accounts} = useGlobalState();
    return (<li className="BookingEntry">
        <div>{accounts.generateParentsString(entry.accountId)} - {accounts.getById(entry.accountId).name}</div>
        <Amount amountInCents={entry.amountInCents}/>
    </li>)
}

