import {useNavigate} from "react-router-dom";
import React, {useEffect, useMemo, useState} from "react";
import {LedgerView} from "../../Websocket/types/ledgers";
import WebsocketService from "../../Websocket/websocketClient";
import Header from "../Header";
import {Checkbox, Container, FormControlLabel} from "@mui/material";
import {BankAccountView} from "../../Websocket/types/bankaccount";
import "./LedgerMain.css";
import {Amount} from "../Amount";
import {LedgerRapportBookingRecord, LedgerRapportNode} from "../../Websocket/types/ledger_rapport";
import ArrowDropDownIcon from '@mui/icons-material/ArrowDropDown';
import ArrowRightIcon from '@mui/icons-material/ArrowRight';
import {formatLocatDayMonth} from "../../utils/formatting";
import IconButton from '@mui/material/IconButton';
import moment from "moment";
import {PeriodSelector} from "../PeriodSelectors/PeriodSelector";
import {useGlobalState} from "../../utils/userstate";

export const LedgerMain = () => {
    const {userState, setUserState} = useGlobalState();
    const [ledger, setLedger] = useState<LedgerView>();

    useEffect(() => {
        if (userState.ledgerId) {
            const subId = WebsocketService.subscribe(
                {type: "getLedgers"},
                n => setLedger(n.readResponse.ledgers?.find(l => l.id === userState.ledgerId))
            );

            return () => WebsocketService.unsubscribe(subId);
        }
    }, [setLedger, userState]);


    return (
        <Container maxWidth="sm" className="App">
            <Header
                title={ledger?.name ?? ""}
            />

            {ledger && <BankAccounts ledger={ledger}/>}
            {ledger && <LedgerRapport ledger={ledger}/>}

        </Container>
    )
}

type BankAccountsProps = {
    ledger: LedgerView;
}
const BankAccounts = ({ledger}: BankAccountsProps) => {
    const [accounts, setAccounts] = useState<BankAccountView[]>();
    const [showHidden, setShowHidden] = useState(false);
    const {setUserState} = useGlobalState();
    let navigate = useNavigate();

    const openBankAccount = (bankAccountId: string) => {
        setUserState(prev => ({
            ...prev,
            bankAccountId
        })).then(() => navigate('/bankaccount'));
    }


    useEffect(() => {
        const subId = WebsocketService.subscribe({
            type: "getBankAccounts",
            ledgerId: ledger.id
        }, n => setAccounts(n.readResponse.bankAccounts))
        return () => WebsocketService.unsubscribe(subId);
    }, [ledger]);

    return (<div>
        <ul className="BankAccountList">
            {accounts?.filter(ba => showHidden || !ba.closed)?.map(a => (
                <li className="BakAccountSummary" key={a.id}
                    onClick={() => openBankAccount(a.id)}>
                    <div className="BakAccountSummaryInfo">
                        <div className="BankName">{a.bank.name}</div>
                        <div style={{display: 'flex', flexDirection: 'row', alignItems: 'center'}}>
                            <div className="BankAccountName">{a.name}</div>
                            <div className="AccountNumber">{a.accountNumber}</div>
                        </div>
                        <div className="BankAccountUnbookedTx">{a.transactionsCounterUnbooked}</div>
                    </div>
                    <div className="BakAccountSummaryAmount"><Amount amountInCents={a.currentBalance}/></div>
                </li>
            ))}
        </ul>
        <FormControlLabel
            label="Show closed"
            control={<Checkbox
                checked={showHidden}
                onChange={(_, checked) => setShowHidden(checked)}
            />}
        />

    </div>);
}

type LedgerRapportProps = {
    ledger: LedgerView;
}
const LedgerRapport = ({ledger}: LedgerRapportProps) => {
    const [ledgerRapport, setLedgerRapport] = useState<LedgerRapportNode[]>([]);
    const {userState, setUserState} = useGlobalState();

    useEffect(() => {
        const subId = WebsocketService.subscribe({
            type: "ledgerRapport",
            ledgerId: ledger.id,
            ledgerRapportRequest: {
                from: userState.legderMainState.currentPeriod.startDateTime,
                toExcluding: userState.legderMainState.currentPeriod.endDateTime,
            }
        }, n => setLedgerRapport(n.readResponse.ledgerRapport!))
        return () => WebsocketService.unsubscribe(subId);
    }, [ledger, userState]);


    return (<div>
        <PeriodSelector periodLocationInUserState={['legderMainState', 'currentPeriod']}/>
        <ul className="LedgerRapportNodes">
            {ledgerRapport.map(n => (
                <li key={n.accountName}>
                    <LedgerRapportSub node={n} level={1}/>
                </li>
            ))}
        </ul>
    </div>)
}

type LedgerRapportSubProps = {
    node: LedgerRapportNode;
    level: number;
}
const LedgerRapportSub = ({node, level, }: LedgerRapportSubProps) => {
    const [collapsed, setCollapsed] = useState(true);

    return (<div className="LedgerRapportNode">
        <div className="LedgerRapportNodeSummary">
            <div>
                {collapsed && <IconButton onClick={() => setCollapsed(!collapsed)}><ArrowRightIcon/></IconButton>}
                {!collapsed && <IconButton onClick={() => setCollapsed(!collapsed)}><ArrowDropDownIcon/></IconButton>}
                {node.accountName}
            </div>
            <div><Amount amountInCents={node.totalAmountInCents}/></div>
        </div>
        {!collapsed && (node.bookingRecords?.length ?? 0) > 0 && <ul className="LedgerRapportNodeRecords">
            {node.bookingRecords?.map(r => (
                <LedgerRapportBookingRecordC key={r.bookingId + '-' + r.id} record={r}/>
            ))}
        </ul>}
        {!collapsed && (node.children?.length ?? 0) > 0 && <ul className="LedgerRapportNodes">
            {node.children?.map(n => (
                <li key={n.accountName}>
                    <LedgerRapportSub node={n} level={1}/>
                </li>
            ))}
        </ul>}
    </div>)
}

type LedgerRapportBookingRecordCProps = {
    record: LedgerRapportBookingRecord;
}
const LedgerRapportBookingRecordC = ({record}: LedgerRapportBookingRecordCProps) => {

    const text = useMemo(() => {

        let str = (record.contraRecords ?? []).map(cr => cr.accountName).join(',');
        if (record.description) {
            str = str + ': ' + record.description;
        }

        return str
    }, [record]);

    return (<li className="LedgerRapportNodeRecord">
        <div style={{display: "flex", flexDirection: "row"}}>
            <div style={{marginRight: '10px', color: '#a2a2a2'}}>{formatLocatDayMonth(moment(record.datetime))}</div>
            <div>{text}</div>
        </div>
        <Amount amountInCents={record.amountInCents}/>
    </li>);
}