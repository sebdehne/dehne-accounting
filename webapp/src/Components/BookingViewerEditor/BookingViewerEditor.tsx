import {Button, Container, FormControl, TextField} from "@mui/material";
import {useNavigate, useParams} from "react-router-dom";
import {useCallback, useEffect, useState} from "react";
import {Booking, BookingEntry} from "../../Websocket/types/bookings";
import {formatIso, formatLocalDayMonthYear} from "../../utils/formatting";
import WebsocketClient from "../../Websocket/websocketClient";
import IconButton from "@mui/material/IconButton";
import EditIcon from "@mui/icons-material/Edit";
import DeleteIcon from "@mui/icons-material/Delete";
import SaveIcon from "@mui/icons-material/Save";
import Header from "../Header";
import "./BookingViewerEditor.css"
import {useDialogs} from "../../utils/dialogs";
import CancelIcon from '@mui/icons-material/Cancel';
import {DatePicker} from "@mui/x-date-pickers";
import AddIcon from "@mui/icons-material/Add";
import {AccountSearchBox} from "../AccountSearchBox/AccountSearchBox";
import {AmountTextField} from "../AmountTextfield/AmountTextfield";
import {clone, removeItemWithSlice} from "../../utils/utils";
import {Amount} from "../Amount";
import {useGlobalState} from "../../utils/globalstate";
import dayjs from "dayjs";

const initialBookingState = {
    entries: [],
    datetime: formatIso(dayjs()),
    id: 0,
    realmId: ""
}

export const BookingViewerEditor = () => {
    const {bookingId} = useParams();
    const [editMode, setEditMode] = useState(!bookingId);
    const [originalBooking, setOriginalBooking] = useState<Booking>();
    const [booking, setBooking] = useState<Booking>(initialBookingState);
    const {showConfirmationDialog} = useDialogs();
    const navigate = useNavigate();

    const deleteBooking = () => {
        if (bookingId) {
            showConfirmationDialog({
                header: "Delete booking?",
                content: "Are you sure, this cannot be undone",
                confirmButtonText: "Delete",
                onConfirmed: () => {
                    WebsocketClient.rpc({
                        type: "deleteBooking",
                        bookingId: parseInt(bookingId)
                    }).then(() => navigate(-1))
                }
            })
        }
    }

    useEffect(() => {
        if (bookingId) {
            let subscribe = WebsocketClient.subscribe(
                {type: "getBooking", getBookingId: parseInt(bookingId)},
                readResponse => {
                    setBooking(readResponse.booking!);
                    setOriginalBooking(clone(readResponse.booking!));
                }
            );
            setEditMode(false);
            return () => WebsocketClient.unsubscribe(subscribe);
        } else {
            setOriginalBooking(undefined);
            setBooking(initialBookingState);
            setEditMode(true);
            return () => {};
        }
    }, [bookingId, setBooking, setOriginalBooking]);


    const removeEntry = useCallback((index: number) => () => {
        setBooking(prevState => ({
            ...prevState,
            entries: removeItemWithSlice(prevState.entries, index)
        }))
    }, [setBooking]);

    const setFilter = useCallback((index: number) => (fn: (prev: BookingEntry) => BookingEntry) => setBooking(prevState => {
        let entries = prevState.entries;
        entries[index] = fn(entries[index]);

        return ({
            ...prevState,
            entries: entries
        });
    }), [setBooking]);

    const sum = booking.entries.reduce((previousValue, currentValue) => previousValue + currentValue.amountInCents, 0);

    const addEntry = useCallback(() => {
        setBooking(prevState => ({
            ...prevState,
            entries: [
                ...prevState.entries,
                {
                    amountInCents: sum * -1,
                    id: prevState.entries.length,
                    accountId: "",
                    checked: false
                }
            ]
        }))
    }, [sum]);

    const isValid = booking.entries.length > 0 && sum === 0 && booking.entries.every(e => !!e.accountId);

    const save = useCallback(() => {
        if (editMode && isValid) {
            WebsocketClient.rpc({
                type: "createOrUpdateBooking",
                createOrUpdateBooking: booking
            }).then(response => {
                if (!bookingId) {
                    navigate('/booking/' + response.editedBookingId, {replace: true})
                }
                setEditMode(false);
            });
        }
    }, [booking, isValid, editMode, bookingId, navigate]);

    return (<Container maxWidth="xs" className="App">
        <Header/>

        <div className="Buttons">
            {editMode && <IconButton disabled={!isValid} onClick={save}><SaveIcon/></IconButton>}
            {!editMode && <IconButton onClick={() => setEditMode(true)}><EditIcon/></IconButton>}
            {bookingId && !editMode && <IconButton onClick={deleteBooking}><DeleteIcon/></IconButton>}
            {bookingId && editMode && <IconButton onClick={() => {
                if (originalBooking) {
                    setBooking(clone(originalBooking));
                }
                setEditMode(false);
            }}><CancelIcon/></IconButton>}
        </div>

        <div className="BookingSummary">
            {editMode && <TextField
                value={booking?.description ?? ''}
                label={'Memo'}
                fullWidth={true}
                onChange={event => setBooking(prevState => ({
                    ...prevState,
                    description: event.target.value ?? ''
                }))}
            />}
            {!editMode && <div style={{display: "flex", flexDirection: "row", justifyContent: "space-between"}}>
                <div> Description:</div>
                <div>{booking.description}</div>
            </div>}

            {editMode && <FormControl sx={{m: 0, width: '100%'}}>
                <DatePicker
                    onChange={value => setBooking(prevState => ({
                        ...prevState!,
                        datetime: formatIso(value!)
                    }))}
                    value={dayjs(booking.datetime)}
                    label={"Date"}
                />
            </FormControl>}
            {!editMode && <div style={{display: "flex", flexDirection: "row", justifyContent: "space-between"}}>
                <div>Date:</div>
                <div>{formatLocalDayMonthYear(dayjs(booking.datetime))}</div>
            </div>}

        </div>

        <div>
            <h4>Entries:</h4>
            {editMode && <div style={{display: "flex", flexDirection: "row", justifyContent: "space-between"}}>
                <Button onClick={addEntry}><AddIcon/> Add</Button>
                {sum !== 0 && <div style={{color: "yellow"}}><Amount amountInCents={sum}/></div>}
            </div>}
            {!editMode && <ul className="BookingEntries">
                {booking.entries.map((e, index) => (
                    <EntryViewer
                        key={index}
                        entry={e}
                    />
                ))}
            </ul>}
            {editMode && <ul className="BookingEntries">
                {booking.entries.map((e, index) => (
                    <EntryEditor
                        key={index}
                        entry={e}
                        setEntry={setFilter(index)}
                        deleteEntry={removeEntry(index)}
                    />
                ))}
            </ul>}

        </div>

    </Container>)
}

type EntryViewerProps = {
    entry: BookingEntry;
}
const EntryViewer = ({entry}: EntryViewerProps) => {
    const {accounts} = useGlobalState();

    if (!accounts.hasData()) return null;

    return (<li className="BookingEntry">
        <div>{accounts.generateParentsString(entry.accountId)} - {accounts.getById(entry.accountId)!.name}</div>
        <div style={{display: "flex", flexDirection: "row", justifyContent: "space-between"}}>
            <div>{entry.description ?? ''}</div>
            <Amount amountInCents={entry.amountInCents}/>
        </div>
    </li>)
}

type EntryEditorProps = {
    entry: BookingEntry;
    setEntry: (fn: (prev: BookingEntry) => BookingEntry) => void;
    deleteEntry: () => void;
}
const EntryEditor = ({entry, setEntry, deleteEntry}: EntryEditorProps) => {
    return (
        <li className="BookingEntry">
            <div style={{display: "flex", flexDirection: "row", justifyContent: "space-between"}}>
                <AccountSearchBox
                    title={"Account"}
                    onSelectedAccountId={(accountId) => {
                        if (accountId) {
                            setEntry((prev: BookingEntry) => ({
                                ...prev,
                                accountId
                            }));
                        }
                    }} value={entry.accountId}/>
                <IconButton onClick={deleteEntry}><DeleteIcon/></IconButton>
            </div>

            <div style={{display: "flex", flexDirection: "row"}}>
                <TextField
                    value={entry.description}
                    onChange={event => setEntry(prev => ({
                        ...prev,
                        description: event.target.value ?? ''
                    }))}
                    label={"Memo"}
                />
                <AmountTextField
                    initialValue={entry.amountInCents}
                    setValue={newValue => setEntry(prev => ({
                        ...prev,
                        amountInCents: newValue
                    }))}
                    fullWidth={false}
                />
            </div>
        </li>
    )
}

