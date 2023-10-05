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

export const AddOrEditBooking = () => {
    const {userState} = useGlobalState();
    const [editBooking, setEditBooking] = useState<BookingView | undefined>(undefined);
    const [recordsInEdit, setRecordsInEdit] = useState<number[]>([]);

    const {bookingId} = useParams();

    const navigate = useNavigate();

    useEffect(() => {
        if (userState && !userState.ledgerId) {
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

    const isValid = editBooking
        && sum === 0
        && editBooking.datetime;

    return (
        <Container maxWidth="sm" className="App">
            <Header title={editBooking ? "Booking: " + editBooking.id : "New booking"}/>

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

                <ul>
                    {editBooking.records.map((r, index) => (<li key={index}>
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

            {sum !== 0 && <div>Sum record is not zero, but: {sum}</div>}

            <div style={{marginTop: '20px'}}>
                <Button disabled={!isValid} variant={"contained"}><SaveIcon/> Save & exit</Button>
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

    return (<div onClick={edit}>
        <div>description: {record.description}</div>
        <div>Category: {categoryParentsPath(categoriesAsList, record.categoryId) + c?.name}</div>
        <Amount amountInCents={record.amount}/>
        <Button onClick={deleteRecord}><DeleteIcon/></Button>
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

    return (<div>

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

        <Button onClick={() => onDone(record)}><SaveIcon></SaveIcon></Button>
    </div>)
}
