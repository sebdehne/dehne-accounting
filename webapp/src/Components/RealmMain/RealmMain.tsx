import Header from "../Header";
import {Container} from "@mui/material";
import React, {useEffect, useState} from "react";
import {useGlobalState} from "../../utils/userstate";
import {useNavigate} from "react-router-dom";
import {PeriodSelectorV2} from "../PeriodSelectors/PeriodSelector";
import {OverviewRapportAccount} from "../../Websocket/types/OverviewRapportAccount";
import WebsocketClient from "../../Websocket/websocketClient";
import "./RealmMain.css"
import {Amount} from "../Amount";
import ArrowRightIcon from "@mui/icons-material/ArrowRight";
import {ArrowDropDownIcon} from "@mui/x-date-pickers";

export const RealmMain = () => {
    const {userStateV2, setUserStateV2, realm} = useGlobalState();
    const [overviewRapport, setOverviewRapport] = useState<OverviewRapportAccount[]>();
    const [totalUnbookedTransactions, setTotalUnbookedTransactions] = useState(0);

    const navigate = useNavigate();

    useEffect(() => {
        if (userStateV2 && !userStateV2.selectedRealm) {
            navigate('/realm', {replace: true});
        }
    }, [userStateV2, navigate]);

    const onHeaderClick = () => {
        setUserStateV2(prev => ({
            ...prev,
            selectedRealm: undefined
        }))
    }

    useEffect(() => {
        const sub = WebsocketClient.subscribe(
            {type: "getOverviewRapport"},
            notify => setOverviewRapport(notify.readResponse.overViewRapport)
        )
        return () => WebsocketClient.unsubscribe(sub);
    }, [setOverviewRapport]);
    useEffect(() => {
        WebsocketClient.subscribe(
            {type: "getTotalUnbookedTransactions"},
            notify => setTotalUnbookedTransactions(notify.readResponse.totalUnbookedTransactions!)
        )
    }, [setTotalUnbookedTransactions]);

    return (
        <Container maxWidth="xs" className="App">
            <Header
                title={realm?.name ?? ""}
                clickable={onHeaderClick}
            />

            {totalUnbookedTransactions > 0 &&
                <div className="TotalUnbookedTransactions">({totalUnbookedTransactions} unbooked transactions)</div>}

            <PeriodSelectorV2/>
            {overviewRapport && <OverviewRapportViewer overviewRapport={overviewRapport}/>}

        </Container>
    );
}


type OverviewRapportViewerProps = {
    overviewRapport: OverviewRapportAccount[]
}
const OverviewRapportViewer = ({overviewRapport}: OverviewRapportViewerProps) => {

    return (<div>
        <ul className="OverviewRapportViewerAccounts">
            {overviewRapport.map((a, index) => (<OverviewRapportViewerAccount
                key={a.name}
                account={a}
                showOpen={a.name !== "Expense" && a.name !== "Income"}
                level={0}
                isLast={index === overviewRapport.length - 1}
            />))}
        </ul>
    </div>)
}


type OverviewRapportViewerAccountProps = {
    account: OverviewRapportAccount;
    showOpen: boolean;
    level: number;
    isLast: boolean;
}
const OverviewRapportViewerAccount = ({account, showOpen, level, isLast}: OverviewRapportViewerAccountProps) => {
    const {localState, setLocalState, accounts} = useGlobalState();

    const overviewRapportAccounts = account.children.filter(a => a.thisPeriod > 0);
    return (<li className="OverviewRapportViewerAccount" style={{marginLeft: (level * 5) + 'px'}}>

        <div
            className="OverviewRapportViewerAccountSummary"
            onClick={() => setLocalState(prev => ({
                ...prev,
                accountTree: prev.accountTree.toggle(account.accountId)
            }))}
        >
            <div className="OverviewRapportViewerAccountSummaryLevel">{!isLast && <MiddleLine/>}{isLast &&
                <LastLine/>}</div>
            <div className="OverviewRapportViewerAccountSummaryMain">
                <div className="OverviewRapportViewerAccountSummaryLeft">
                    {overviewRapportAccounts.length > 0 &&
                        <div>
                            {localState.accountTree.isExpanded(account.accountId) &&
                                <ArrowDropDownIcon fontSize={"small"}/>}
                            {!localState.accountTree.isExpanded(account.accountId) &&
                                <ArrowRightIcon fontSize={"small"}/>}
                        </div>
                    }
                    {overviewRapportAccounts.length === 0 && <div style={{margin: '8px'}}></div>}
                    <div>
                        {accounts.getById(account.accountId).name}
                    </div>
                </div>
                <div className="OverviewRapportViewerAccountSummaryRight">
                    <div style={{fontSize: "small", color: "#a8a8a8"}}><Amount
                        amountInCents={account.openBalance}/></div>
                    <div style={{fontSize: "larger"}}><Amount amountInCents={account.thisPeriod}/></div>
                    <div style={{fontSize: "small", color: "#a8a8a8"}}><Amount
                        amountInCents={account.closeBalance}/></div>
                </div>
            </div>
        </div>

        {localState.accountTree.isExpanded(account.accountId) && <ul className="OverviewRapportViewerAccounts">
            {overviewRapportAccounts
                .map((c, index) => (<OverviewRapportViewerAccount
                    key={c.name}
                    account={c}
                    showOpen={showOpen}
                    level={level + 1}
                    isLast={index === overviewRapportAccounts.length - 1}
                />))}
        </ul>}
    </li>)
}

const lineHeight = 110;
const lineWidth = 14;
const lineColor = "#252525"

const MiddleLine = () => {
    return (<svg height={lineHeight} width={lineWidth}>
        <line x1={0} y1="0" x2={0} y2={lineHeight} style={{
            stroke: lineColor,
            strokeWidth: 2
        }}/>
        <line x1={0} y1={lineHeight / 2} x2={lineWidth} y2={lineHeight / 2} style={{
            stroke: lineColor,
            strokeWidth: 2
        }}/>
    </svg>);
}
const LastLine = () => {
    return (<svg height={lineHeight} width={lineWidth}>
        <line x1={0} y1={0} x2={0} y2={lineHeight / 2} style={{
            stroke: lineColor,
            strokeWidth: 2
        }}/>
        <line x1={0} y1={lineHeight / 2} x2={lineWidth} y2={lineHeight / 2} style={{
            stroke: lineColor,
            strokeWidth: 2
        }}/>
    </svg>);
}

type LabeledNumberRowProps = {
    label: string;
    first: React.ReactNode;
    second: React.ReactNode;
    onClick: () => void;
}
const LabeledNumberRow = ({
                              label,
                              first,
                              second,
                              onClick
                          }: LabeledNumberRowProps) => {

    return (<div className="LabeledNumberRow" onClick={onClick}>
        <div style={{width: '140px'}}>
            {label}
        </div>
        <div style={{width: '120px', textAlign: "end"}}>
            {first}
        </div>
        <div style={{width: '120px', textAlign: "end"}}>
            {second}
        </div>
    </div>)
}

