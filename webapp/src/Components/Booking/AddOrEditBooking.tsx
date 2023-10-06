import Header from "../Header";
import React, {useEffect, useState} from "react";
import {Button, Container, FormControl, TextField} from "@mui/material";
import {useGlobalState} from "../../utils/userstate";
import {useNavigate, useParams} from "react-router-dom";
import WebsocketService from "../../Websocket/websocketClient";
import "./AddOrEditBooking.css"
import {BookingRecordView, BookingView} from "../../Websocket/types/bookings";
import {categoryParentsPath, formatIso} from "../../utils/formatting";
import {Amount} from "../Amount";
import SaveIcon from '@mui/icons-material/Save';
import {CategorySearchBox2} from "../CategorySearchBox/CategorySearchBox2";
import {DatePicker} from "@mui/x-date-pickers";
import moment from "moment";
import AddIcon from "@mui/icons-material/Add";
import DeleteIcon from "@mui/icons-material/Delete";
import {removeItemWithSlice} from "../../utils/utils";
import {LedgerView} from "../../Websocket/types/ledgers";

export const AddOrEditBooking = () => {
    const {userState} = useGlobalState();
    const [ledger, setLedger] = useState<LedgerView>();
    const [editBooking, setEditBooking] = useState<BookingView | undefined>(undefined);
    const [recordsInEdit, setRecordsInEdit] = useState<number[]>([]);

    const {bookingId} = useParams();

    const navigate = useNavigate();

    useEffect(() => {
        if (userState?.ledgerId) {
            const subId = WebsocketService.subscribe(
                {type: "getLedgers"},
                n => setLedger(n.readResponse.ledgers?.find(l => l.id === userState.ledgerId))
            );

            return () => WebsocketService.unsubscribe(subId);
        } else if (userState && !userState.ledgerId) {
            navigate('/ledger', {replace: true});
        }
    }, [userState, navigate]);

    useEffect(() => {
        if (userState?.ledgerId && bookingId && !editBooking) {
            WebsocketService.subscribe(
                {
                    type: "getBooking",
                    ledgerId: userState?.ledgerId,
                    getBookingId: parseInt(bookingId)
                },
                notify => setEditBooking(notify.readResponse.getBookingResponse)
            )
        }

        if (userState?.ledgerId && !bookingId && !editBooking) {
            setEditBooking(({
                id: 0,
                description: undefined,
                datetime: formatIso(moment()),
                records: [],
                ledgerId: userState.ledgerId
            }))
        }
    }, [userState, bookingId, editBooking, setEditBooking]);

    const onRecordEditDone = (id: number, r: BookingRecordView) => {
        setRecordsInEdit([...recordsInEdit.filter(d => d !== id)]);
        setEditBooking(prevState => {
            prevState!.records[id] = r
            return prevState;
        });
    }

    const sum = (editBooking?.records ?? [])
        .map(value => value.amount)
        .reduce((a, b) => a + b, 0);
    const haveAmounts = (editBooking?.records ?? [])
        .every(value => Math.abs(value.amount) > 0);
    const haveCategories = (editBooking?.records ?? [])
        .every(value => !!value.categoryId);

    const isValid = editBooking
        && sum === 0
        && haveAmounts
        && haveCategories
        && !!editBooking.datetime
        && recordsInEdit.length === 0;

    const saveAndExit = () => {
        WebsocketService.rpc({
            type: "addOrReplaceBooking",
            addOrReplaceBooking: editBooking
        }).then(() => navigate("/bookings", {replace: true}))
    }

    return (
        <Container maxWidth="sm" className="App">
            <Header title={
                (bookingId ? "Booking: " + editBooking?.id : "New booking") +
                    (ledger?.name ? " - Ledger: " + ledger.name : "")
            }/>

            {editBooking && <>

                <FormControl sx={{m: 1, width: '100%'}}>
                    <TextField
                        value={editBooking.description ?? ''}
                        label="Description"
                        onChange={event => {
                            setEditBooking(prevState => ({
                                ...prevState!,
                                description: event.target.value ?? undefined
                            }))
                        }}
                    />

                </FormControl>
                <FormControl sx={{m: 1, width: '100%'}}>
                    <DatePicker
                        onChange={value => setEditBooking(prevState => ({
                            ...prevState!,
                            datetime: formatIso(value!)
                        }))}
                        value={moment(editBooking.datetime)}
                        label={"Date"}
                    />
                </FormControl>

                <ul className="RecordUl">
                    {editBooking.records.map((r, index) => (<li key={index} className="RecordLi">
                        {recordsInEdit.includes(index) && <AddOrEditRecord
                            onDone={r => onRecordEditDone(index, r)}
                            record={r}
                        />}
                        {!recordsInEdit.includes(index) && <RecordViewer
                            record={r}
                            edit={() => setRecordsInEdit(prevState => ([...prevState, index]))}
                            deleteRecord={() => {
                                setRecordsInEdit([]);
                                setEditBooking(prevState => ({
                                    ...prevState!,
                                    records: removeItemWithSlice(prevState!.records, index)
                                }))
                            }}
                        />}
                    </li>))}
                </ul>

                <Button
                    variant={"outlined"}
                    onClick={() => {
                        setEditBooking(prevState => ({
                            ...prevState!,
                            records: [
                                ...prevState!.records,
                                {
                                    ledgerId: editBooking.ledgerId,
                                    bookingId: editBooking.id,
                                    id: 0,
                                    categoryId: '',
                                    amount: 0
                                }
                            ]
                        }));
                    }}
                ><AddIcon/>Add record</Button>
            </>}


            <div style={{marginTop: '20px', display: "flex", flexDirection: "row", alignItems: "center"}}>
                <Button disabled={!isValid} variant={"contained"} onClick={saveAndExit}><SaveIcon/> Save & exit</Button>
                {sum !== 0 && <div style={{marginLeft: '20px'}}>Sum record is not zero, but: {sum}</div>}
            </div>

        </Container>
    )
}

type  RecordViewerProps = {
    record: BookingRecordView;
    edit: () => void;
    deleteRecord: () => void;
}
const RecordViewer = ({record, edit, deleteRecord}: RecordViewerProps) => {
    const {categoriesAsList} = useGlobalState();

    const c = categoriesAsList.find(c => c.id === record.categoryId);

    return (
        <div className={record.amount > 0 ? "RecordInViewPositiveAmount" : "RecordInViewNegativeAmount"} onClick={edit}>
            <div className="RecordInViewLeft">
                <div>description: {record.description}</div>
                <div>Category: {categoryParentsPath(categoriesAsList, record.categoryId) + c?.name}</div>
                <div style={{display: "flex", flexDirection: "row"}}>Amount: <Amount amountInCents={record.amount}/>
                </div>
            </div>
            <div className="RecordInViewRight">
                <Button onClick={deleteRecord}><DeleteIcon/></Button>
            </div>

        </div>)
}

type AddOrEditRecordProps = {
    onDone: (r: BookingRecordView) => void;
    record: BookingRecordView;
}
const AddOrEditRecord = ({onDone, record: orig}: AddOrEditRecordProps) => {
    const [record, setRecord] = useState(
        JSON.parse(JSON.stringify(orig)) as BookingRecordView
    );

    return (<div className="RecordInEdit">
        <div className="RecordInEditLeft">
            <FormControl sx={{m: 1, width: '100%'}}>
                <TextField
                    value={record.description ?? ''}
                    label="Description"
                    onChange={event => {
                        setRecord(prevState => ({
                            ...prevState,
                            description: event.target.value ?? undefined
                        }));
                    }}
                />
            </FormControl>

            <CategorySearchBox2
                allowEmpty={false}
                includeIntermediate={true}
                onSelectedCategoryId={categoryId => setRecord(prevState => ({
                    ...prevState,
                    categoryId: categoryId!
                }))}
                value={record.categoryId}
            />

            <FormControl sx={{m: 1, width: '100%'}}>
                <TextField
                    value={record.amount}
                    label="Amount in cents"
                    onChange={event => {
                        const value = parseInt(event.target.value ?? '0');
                        setRecord(prevState => ({
                            ...prevState,
                            amount: value
                        }));
                    }}
                />
            </FormControl>
        </div>
        <div className="RecordInEditRight">
            <Button onClick={() => onDone(record)}><SaveIcon></SaveIcon></Button>
        </div>
    </div>)
}
