import {Checkbox, Container} from "@mui/material";
import Header from "../Header";
import React, {useCallback, useEffect, useMemo, useState} from "react";
import "./BookingForAccountViewer.css";
import {PeriodSelectorV2} from "../PeriodSelectors/PeriodSelector";
import {Booking, BookingEntry} from "../../Websocket/types/bookings";
import WebsocketClient from "../../Websocket/websocketClient";
import {useGlobalState} from "../../utils/globalstate";
import {Amount} from "../Amount";
import {useNavigate, useParams} from "react-router-dom";
import {DateViewer} from "../PeriodSelectors/DateViewer";
import {useDialogs} from "../../utils/dialogs";
import InputIcon from "@mui/icons-material/Input";
import IconButton from "@mui/material/IconButton";
import DeleteIcon from "@mui/icons-material/Delete";
import {Loading} from "../loading";

export const BookingForAccountViewer = () => {
    const [bookings, setBookings] = useState<Booking[]>([]);
    const [balance, setBalance] = useState(0);
    const {accountId} = useParams();
    const {accounts, userStateV2, setUserStateV2} = useGlobalState();
    const [onlyShowUnbooked, setOnlyShowUnbooked] = useState(false);

    useEffect(() => {
        if (accountId) {
            const sub = WebsocketClient.subscribe(
                {type: "getBookings", accountId},
                readResponse => {
                    setBookings(readResponse.bookings!);
                    setBalance(readResponse.bookingsBalance!)
                }
            );
            return () => WebsocketClient.unsubscribe(sub);
        }

    }, [setBookings, accountId]);

    const [sumPositive, sumNegative, sum, sumChecked, sumUnchecked] = useMemo(() => {
        if (!accountId) return [0, 0, 0, 0, 0];
        let sumPositive = 0;
        let sumNegative = 0;
        let sumChecked = 0;
        let sumUnchecked = 0;

        bookings.forEach(b => {
            b.entries.filter(e => e.accountId === accountId).forEach(e => {
                if (e.amountInCents > 0) {
                    sumPositive += e.amountInCents;
                } else {
                    sumNegative += e.amountInCents;
                }
                if (e.checked) {
                    sumChecked += e.amountInCents;
                } else {
                    sumUnchecked += e.amountInCents;
                }
            });
        });

        return [sumPositive, sumNegative, sumPositive + sumNegative, sumChecked, sumUnchecked];
    }, [bookings, accountId]);

    const navigate = useNavigate();

    const onImport = () => {
        navigate('/bankaccount_tx/' + accountId + '/import');
    }

    const {showConfirmationDialog} = useDialogs();

    const onDeleteAll = () => {
        showConfirmationDialog({
            onConfirmed: () => WebsocketClient.rpc({type: "deleteAllUnbookedTransactions", "accountId": accountId}),
            content: <p>Are you sure you want to delete all unbooked transactions? This cannot be undone.</p>,
            header: "Delete all unbooked transactions?",
        })
    }

    const editMode = !!userStateV2?.frontendState?.bookingsEditMode;

    const thisAccount = accountId ? accounts.getById(accountId) : undefined;
    if (!thisAccount) return <Loading/>;

    return (<Container maxWidth="xs" className="App">
        <Header
            title={accountId ? accounts.getById(accountId)!.name : ''}
            subTitle={accountId ? accounts.generateParentsString(accountId) : ''}
            extraMenuOptions={[
                ['Import transactions', onImport],
                ['Delete all unbooked', onDeleteAll],
                [onlyShowUnbooked ? 'Show all' : 'Only show unbooked', () => setOnlyShowUnbooked(!onlyShowUnbooked)],
                [editMode ? 'Exit edit' : 'Edit mode', () => setUserStateV2(prev => ({
                    ...prev,
                    frontendState: {
                        ...prev.frontendState,
                        bookingsEditMode: !editMode
                    }
                }))]
            ]}
        />

        <PeriodSelectorV2/>

        <div className="Sums">
            <div className="Sum">
                <div>Open balance</div>
                <div><Amount amountInCents={balance}/></div>
            </div>

            <div className="Sum">
                <div>Sum positive</div>
                <div><Amount amountInCents={sumPositive}/></div>
            </div>
            <div className="Sum">
                <div>Sum negative</div>
                <div><Amount amountInCents={sumNegative}/></div>
            </div>
            <div className="Sum">
                <div>Sum</div>
                <div><Amount amountInCents={sum}/></div>
            </div>
            {editMode && <div className="Sum">
                <div>Sum checked</div>
                <div><Amount amountInCents={sumChecked}/></div>
            </div>}
            {editMode && <div className="Sum">
                <div>Sum unchecked</div>
                <div><Amount amountInCents={sumUnchecked}/></div>
            </div>}
            <div className="Sum">
                <div>Close balance</div>
                <div><Amount amountInCents={balance + sum}/></div>
            </div>

        </div>

        {accountId && <ul className="Bookings">
            {bookings
                .filter(value => !onlyShowUnbooked || value.entries.length == 0)
                .flatMap(b => {

                    if (b.entries.length > 0) {
                        const entries = b.entries.filter(e => e.accountId === accountId);
                        return entries.map(e =>
                            <BookingViewer key={`${b.id}-${e.id}`}
                                           booking={b as Booking}
                                           entry={e as BookingEntry}
                                           showChecked={editMode}
                            />
                        );
                    } else {
                        return [
                            <UnbookedTxViewer key={b.id}
                                              accountId={accountId}
                                              unbookedAmountInCents={b.unbookedAmountInCents!}
                                              id={b.id}
                                              datetime={b.datetime}
                                              description={b.description}
                                              editMode={editMode}
                            />
                        ]
                    }
                })
            }
        </ul>}
    </Container>)
}


const BookingViewer = ({booking, entry, showChecked}: {
    booking: Booking;
    entry: BookingEntry;
    showChecked: boolean;
}) => {
    const {accounts} = useGlobalState();
    const navigate = useNavigate();
    const {showConfirmationDialog} = useDialogs();

    const toggleChecked = useCallback(() => {
        WebsocketClient.rpc({
            type: "updateChecked",
            bookingId: booking.id,
            bookingEntryId: entry.id,
            bookingEntryChecked: !entry.checked,
        })
    }, [entry]);

    const deleteBooking = useCallback(() => {
        showConfirmationDialog({
            header: "Delete booking?",
            content: "Are you sure, this cannot be undone",
            confirmButtonText: "Delete",
            onConfirmed: () => {
                WebsocketClient.rpc({
                    type: "deleteBooking",
                    bookingId: booking.id
                })
            }
        });
    }, [booking]);

    let otherEntries = booking.entries.filter(e => e.id !== entry.id);
    return (<li className="BookingEntryContainer">
        <div className="BookingEntryLeft">
            <div className="BookingEntrySummary">
                <div style={{display: "flex", flexDirection: "row"}}>
                    <div
                        onClick={() => navigate('/booking/' + booking.id)}
                        style={{marginRight: '10px'}}
                    ><DateViewer date={booking.datetime}/></div>
                    <div>{booking.description ?? entry.description}</div>
                </div>
                <div style={{fontSize: "larger", fontWeight: "bold"}}><Amount amountInCents={entry.amountInCents}/>
                </div>
            </div>
            <ul className="OtherEntries">
                {otherEntries.map(e => (<li key={e.id} className="OtherEntry">
                    <div
                        onClick={() => navigate('/bookings/' + e.accountId)}>{accounts.generateParentsString(e.accountId)} - {accounts.getById(e.accountId)!.name}</div>
                    {otherEntries.length > 1 && <Amount amountInCents={e.amountInCents}/>}
                </li>))}
            </ul>
        </div>
        {showChecked && <div className="BookingEntryRight">
            <Checkbox checked={entry.checked} onClick={() => toggleChecked()}/>
            <IconButton onClick={() => deleteBooking()}><DeleteIcon/></IconButton>
        </div>}
    </li>)
}

const UnbookedTxViewer = ({id, accountId, unbookedAmountInCents, description, datetime, editMode}: {
    accountId: string;
    unbookedAmountInCents: number;
    id: number;
    datetime: string;
    description?: string;
    editMode: boolean;
}) => {
    const navigate = useNavigate();
    const {showConfirmationDialog} = useDialogs();

    const book = (txId: number) => {
        navigate('/matchers/' + accountId + '/' + txId);
    }

    const deleteUnbookedTx = (txId: number) => {
        if (accountId) {
            showConfirmationDialog({
                header: "Delete unbooked transaction?",
                content: "Are you sure? This cannot be undone",
                onConfirmed: () => {
                    WebsocketClient.rpc({
                        type: "deleteUnbookedTransaction",
                        accountId,
                        deleteUnbookedBankTransactionId: txId
                    })
                }
            })
        }
    }

    return (<li className="UnbookedEntryContainer">
        <div className="UnbookedEntry">
            <div className="UnbookedSummary">
                <div style={{display: "flex", flexDirection: "row"}}>
                    <div style={{marginRight: '10px'}}
                    ><DateViewer date={datetime}/></div>
                    <div>{description}</div>
                </div>
                <div style={{fontSize: "larger", fontWeight: "bold"}}><Amount amountInCents={unbookedAmountInCents}/>
                </div>
            </div>
        </div>
        {editMode && <div className="UnbookedRight">
            <IconButton onClick={() => deleteUnbookedTx(id)}><DeleteIcon/></IconButton>
            <IconButton onClick={() => book(id)}><InputIcon/></IconButton>
        </div>}
    </li>)
}

