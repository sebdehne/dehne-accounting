import Header from "../Header";
import {Container, FormControl, FormControlLabel, Switch, TextField} from "@mui/material";
import React, {useCallback, useEffect, useState} from "react";
import {useGlobalState} from "../../utils/userstate";
import {LedgerView} from "../../Websocket/types/ledgers";
import WebsocketService from "../../Websocket/websocketClient";
import WebsocketClient from "../../Websocket/websocketClient";
import {BookingView} from "../../Websocket/types/bookings";
import {PeriodSelector} from "../PeriodSelectors/PeriodSelector";
import './Bookings.css';
import {categoryParentsPath, formatLocatDayMonth} from "../../utils/formatting";
import moment from "moment";
import {Amount} from "../Amount";
import DeleteIcon from "@mui/icons-material/Delete";
import IconButton from "@mui/material/IconButton";

export const Bookings = () => {
    const {userState, categoriesAsList} = useGlobalState();
    const [ledger, setLedger] = useState<LedgerView>();
    const [bookings, setBookings] = useState<BookingView[]>([]);
    const [editMode, setEditMode] = useState(false);
    const [filter, setFilter] = useState('');

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
    }, [setBookings, ledger, userState.bookingsState]);

    const deleteBooking = useCallback((bookingId: number) => {
        if (ledger) {
            WebsocketClient.rpc({
                type: "removeBooking",
                ledgerId: ledger.id,
                bookingId
            })
        }
    }, [ledger]);

    const getCategory = useCallback((categoryId: string) =>
            categoriesAsList.find(c => c.id === categoryId)!,
        [categoriesAsList]
    );

    const filterFn = (b: BookingView): boolean => {

        const strings = [
            b.description,
            ...b.records.map(r => r.description),
            ...b.records.map(r => getCategory(r.categoryId).name),
            ...b.records.map(r => categoryParentsPath(categoriesAsList, getCategory(r.categoryId).parentCategoryId)),
        ].filter(s => !!s)
            .map(s => s!.toLowerCase());

        return !filter || !!strings.find(s => s.includes(filter));
    }

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

            <div>
                <FormControl sx={{m: 1, width: '100%'}}>
                    <TextField
                        value={filter}
                        label="Filter"
                        onChange={event => setFilter(event.target.value ?? '')}
                    />
                </FormControl>
            </div>

            <ul className="Bookings">
                {bookings.filter(filterFn).map(b => (<li key={b.id} className="Booking">
                    <div className="BookingHeader">
                        <div className="BookingHeaderSummary">
                            <div>{formatLocatDayMonth(moment(b.datetime))}</div>
                            {b.description && <div className="BookingHeaderSummaryDescription"> - {b.description}</div>}
                        </div>
                        <div className="BookingHeaderRight">
                            {editMode && <IconButton
                                size={"small"}
                                onClick={() => deleteBooking(b.id)}
                            > <DeleteIcon fontSize="inherit"/> </IconButton>
                            }
                        </div>

                    </div>
                    <ul className="BookingRecords">
                        {b.records.map(r => (<li key={r.id} className="BookingRecord">
                            <div className="BookingRecordLeft">
                                <div className="BookingRecordLeftCategory">
                                    <div className="BookingRecordLeftCategoryPath">
                                        {categoryParentsPath(categoriesAsList, getCategory(r.categoryId).parentCategoryId)}
                                    </div>
                                    {getCategory(r.categoryId).name}
                                </div>
                                {r.description &&
                                    <div className="BookingRecordLeftDescription"> - {r.description}</div>}
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