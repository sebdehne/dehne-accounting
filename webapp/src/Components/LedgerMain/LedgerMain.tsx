import {useNavigate, useParams} from "react-router-dom";
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
import {formatIso, formatLocatDayMonth, monthDelta, startOfCurrentMonth} from "../../utils/formatting";
import IconButton from '@mui/material/IconButton';
import moment from "moment";
import {MonthPeriodSelector} from "../PeriodSelectors/MonthPeriodSelector";

export const LedgerMain = () => {
    const {ledgerId} = useParams()
    const [ledger, setLedger] = useState<LedgerView>();

    useEffect(() => {
        if (ledgerId) {
            const subId = WebsocketService.subscribe(
                {type: "getLedgers"},
                n => setLedger(n.readResponse.ledgers?.find(l => l.id === ledgerId))
            );

            return () => WebsocketService.unsubscribe(subId);
        } else {
            return;
        }
    }, [setLedger, ledgerId]);


    return (
        <Container maxWidth="sm" className="App">
            <Header
                title={ledger?.name ?? ""}
                backName={"Back"}
                backUrl={'/'}
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

    let navigate = useNavigate();

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
                    onClick={() => navigate('/ledger/' + ledger.id + '/bankaccount/' + a.id)}>
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
    const [period, setPeriod] = useState<moment.Moment[]>([
        startOfCurrentMonth(),
        monthDelta(startOfCurrentMonth(), 1)
    ]);

    useEffect(() => {
        const subId = WebsocketService.subscribe({
            type: "ledgerRapport",
            ledgerId: ledger.id,
            ledgerRapportRequest: {
                from: formatIso(period[0]),
                toExcluding: formatIso(period[1]),
            }
        }, n => setLedgerRapport(n.readResponse.ledgerRapport!))
        return () => WebsocketService.unsubscribe(subId);
    }, [ledger, period]);


    return (<div>
        <MonthPeriodSelector period={period} setPeriod={setPeriod}/>
        <ul className="LedgerRapportNodes">
            {ledgerRapport.map(n => (
                <li key={n.accountName}>
                    <LedgerRapportSub node={n} level={1}
                                      negateAmount={n.accountName === "Expense" || n.accountName === "Income"}/>
                </li>
            ))}
        </ul>
    </div>)
}

type LedgerRapportSubProps = {
    node: LedgerRapportNode;
    level: number;
    negateAmount: boolean;
}
const LedgerRapportSub = ({node, level, negateAmount}: LedgerRapportSubProps) => {
    const [collapsed, setCollapsed] = useState(true);

    return (<div className="LedgerRapportNode">
        <div className="LedgerRapportNodeSummary">
            <div>
                {collapsed && <IconButton onClick={() => setCollapsed(!collapsed)}><ArrowRightIcon/></IconButton>}
                {!collapsed && <IconButton onClick={() => setCollapsed(!collapsed)}><ArrowDropDownIcon/></IconButton>}
                {node.accountName}
            </div>
            <div><Amount amountInCents={node.totalAmountInCents * (negateAmount ? -1 : 1)}/></div>
        </div>
        {!collapsed && (node.bookingRecords?.length ?? 0) > 0 && <ul className="LedgerRapportNodeRecords">
            {node.bookingRecords?.map(r => (
                <LedgerRapportBookingRecordC key={r.bookingId + '-' + r.id} record={r} negateAmount={negateAmount}/>
            ))}
        </ul>}
        {!collapsed && (node.children?.length ?? 0) > 0 && <ul className="LedgerRapportNodes">
            {node.children?.map(n => (
                <li key={n.accountName}>
                    <LedgerRapportSub node={n} level={1} negateAmount={negateAmount}/>
                </li>
            ))}
        </ul>}
    </div>)
}

type LedgerRapportBookingRecordCProps = {
    record: LedgerRapportBookingRecord;
    negateAmount: boolean;
}
const LedgerRapportBookingRecordC = ({record, negateAmount}: LedgerRapportBookingRecordCProps) => {

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
        <Amount amountInCents={record.amountInCents * (negateAmount ? -1 : 1)}/>
    </li>);
}