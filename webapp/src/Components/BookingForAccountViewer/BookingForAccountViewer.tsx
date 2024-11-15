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
    const onlyShowUnbooked = userStateV2?.frontendState?.onlyShowUnbooked ?? false;

    const bookingsWithBalance: EntryData[] = useMemo(() => {

        let currentBalance = balance;

        return bookings
            .toReversed()
            .flatMap(b => {
                if (b.entries.length === 0) {
                    currentBalance += b.unbookedAmountInCents ?? 0;
                    return [
                        {
                            bookingId: b.id,
                            bookingEntryId: b.id,
                            amountInCents: b.unbookedAmountInCents ?? 0,
                            balance: currentBalance,
                            datetime: b.datetime,
                            otherEntries: [],
                            accountId: accountId,
                            description: b.description,
                            checked: false,
                        } as EntryData
                    ]
                } else {
                    let entries = b.entries.filter(e => e.accountId === accountId);
                    return entries.map(e => {
                        currentBalance += e.amountInCents;
                        return {
                            bookingId: b.id,
                            bookingEntryId: e.id,
                            amountInCents: e.amountInCents,
                            balance: currentBalance,
                            datetime: b.datetime,
                            otherEntries: b.entries.filter(oe => oe.id !== e.id),
                            accountId: accountId,
                            description: e.description ?? b.description,
                            checked: e.checked,
                        } as EntryData;
                    })
                }
            })
            .filter(value => !onlyShowUnbooked || value.otherEntries.length == 0)
            .toReversed()
            ;
    }, [accountId, bookings, balance, onlyShowUnbooked]);

    const thisAccount = accountId ? accounts.getById(accountId) : undefined;
    if (!thisAccount) return <Loading/>;

    return (<Container maxWidth="xs" className="App">
        <Header
            title={accountId ? accounts.getById(accountId)!.name : ''}
            subTitle={accountId ? accounts.generateParentsString(accountId) : ''}
            extraMenuOptions={[
                ['Import transactions', onImport],
                ['Delete all unbooked', onDeleteAll],
                [onlyShowUnbooked ? 'Show all' : 'Only show unbooked', () => setUserStateV2(prev => ({
                    ...prev,
                    frontendState: {
                        ...prev.frontendState,
                        onlyShowUnbooked: !onlyShowUnbooked
                    }
                }))],
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
            {bookingsWithBalance
                .map(b => {
                    if (b.otherEntries.length === 0) {
                        return <UnbookedTxViewer key={b.bookingId}
                                                 entryData={b}
                                                 editMode={editMode}
                        />
                    } else {
                        return <BookingViewer key={`${b.bookingId}-${b.bookingEntryId}`}
                                              entryData={b}
                                              showChecked={editMode}
                        />
                    }
                })
            }
        </ul>}
    </Container>)
}

type EntryData = {
    bookingId: number;
    bookingEntryId: number;
    amountInCents: number;
    balance: number;
    datetime: string;
    otherEntries: BookingEntry[];
    accountId: string;
    description?: string;
    checked: boolean;
}

const BookingViewer = ({
                           showChecked,
                           entryData,
                       }: {
    showChecked: boolean;
    entryData: EntryData;
}) => {
    const {accounts} = useGlobalState();
    const navigate = useNavigate();
    const {showConfirmationDialog} = useDialogs();

    const toggleChecked = useCallback(() => {
        WebsocketClient.rpc({
            type: "updateChecked",
            bookingId: entryData.bookingId,
            bookingEntryId: entryData.bookingEntryId,
            bookingEntryChecked: !entryData.checked,
        })
    }, [entryData]);

    const deleteBooking = useCallback(() => {
        showConfirmationDialog({
            header: "Delete booking?",
            content: "Are you sure, this cannot be undone",
            confirmButtonText: "Delete",
            onConfirmed: () => {
                WebsocketClient.rpc({
                    type: "deleteBooking",
                    bookingId: entryData.bookingId
                })
            }
        });
    }, [entryData]);

    return (<li className="BookingEntryContainer">
        <div className="BookingEntryLeft">
            <div className="BookingEntrySummary">
                <div style={{display: "flex", flexDirection: "row"}}>
                    <div
                        onClick={() => navigate('/booking/' + entryData.bookingId)}
                        style={{marginRight: '10px'}}
                    ><DateViewer date={entryData.datetime}/></div>
                    <div>{entryData.description}</div>
                </div>
                <div style={{display: "flex", flexDirection: "column", alignItems: "flex-end"}}>
                    <div style={{fontSize: "larger", fontWeight: "bold"}}>
                        <Amount amountInCents={entryData.amountInCents}/>
                    </div>
                    <div style={{color: "#a4a4a4"}}>
                        <Amount amountInCents={entryData.balance}/>
                    </div>
                </div>
            </div>
            <ul className="OtherEntries">
                {entryData.otherEntries.map(e => (<li key={e.id} className="OtherEntry">
                    <div
                        onClick={() => navigate('/bookings/' + e.accountId)}>{accounts.generateParentsString(e.accountId)} - {accounts.getById(e.accountId)!.name}</div>
                    {entryData.otherEntries.length > 1 && <Amount amountInCents={e.amountInCents}/>}
                </li>))}
            </ul>
        </div>
        {showChecked && <div className="BookingEntryRight">
            <Checkbox checked={entryData.checked} onClick={() => toggleChecked()}/>
            <IconButton onClick={() => deleteBooking()}><DeleteIcon/></IconButton>
        </div>}
    </li>)
}

const UnbookedTxViewer = ({entryData, editMode}: {
    entryData: EntryData;
    editMode: boolean;
}) => {
    const navigate = useNavigate();
    const {showConfirmationDialog} = useDialogs();

    const book = (txId: number) => {
        navigate('/matchers/' + entryData.accountId + '/' + txId);
    }

    const deleteUnbookedTx = (txId: number) => {
        showConfirmationDialog({
            header: "Delete unbooked transaction?",
            content: "Are you sure? This cannot be undone",
            onConfirmed: () => {
                WebsocketClient.rpc({
                    type: "deleteUnbookedTransaction",
                    accountId: entryData.accountId,
                    deleteUnbookedBankTransactionId: txId
                })
            }
        })
    }

    return (<li className="UnbookedEntryContainer">
        <div className="UnbookedEntry">
            <div className="UnbookedSummary">
                <div style={{display: "flex", flexDirection: "row"}}>
                    <div style={{marginRight: '10px'}}
                    ><DateViewer date={entryData.datetime}/></div>
                    <div>{entryData.description}</div>
                </div>
                <div style={{display: "flex", flexDirection: "column", alignItems: "flex-end"}}>
                    <div style={{fontSize: "larger", fontWeight: "bold"}}>
                        <Amount amountInCents={entryData.amountInCents}/>
                    </div>
                    <div style={{color: "#a4a4a4"}}>
                        <Amount amountInCents={entryData.balance}/>
                    </div>
                </div>

            </div>
        </div>
        {editMode && <div className="UnbookedRight">
            <IconButton onClick={() => deleteUnbookedTx(entryData.bookingId)}><DeleteIcon/></IconButton>
            <IconButton onClick={() => book(entryData.bookingId)}><InputIcon/></IconButton>
        </div>}
    </li>)
}

