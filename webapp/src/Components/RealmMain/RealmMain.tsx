import Header from "../Header";
import {Container, FormControlLabel, Switch} from "@mui/material";
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
import IconButton from "@mui/material/IconButton";

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
    const [onlyIfThisPeriod, setOnlyIfThisPeriod] = useState(false);

    return (<div>
        <div style={{display: "flex", flexDirection: "row", justifyContent: "flex-end"}}>
            <FormControlLabel control={<Switch value={onlyIfThisPeriod} onChange={(event, checked) => setOnlyIfThisPeriod(checked)} />} label="Hide empty" labelPlacement={"start"}/>
        </div>

        <ul className="OverviewRapportViewerAccounts">
            {overviewRapport.map((a, index) => (<OverviewRapportViewerAccount
                key={a.name}
                account={a}
                level={0}
                isLast={index === overviewRapport.length - 1}
                filter={a => onlyIfThisPeriod ? a.thisPeriod !== 0 : true}
            />))}
        </ul>
    </div>)
}


type OverviewRapportViewerAccountProps = {
    account: OverviewRapportAccount;
    level: number;
    isLast: boolean;
    filter: (a: OverviewRapportAccount) => boolean;
}
const OverviewRapportViewerAccount = ({account, level, isLast, filter}: OverviewRapportViewerAccountProps) => {
    const {localState, setLocalState, accounts} = useGlobalState();
    const navigate = useNavigate();

    const overviewRapportAccounts = account.children.filter(filter);

    if (!accounts.hasData()) return null;

    return (<li className="OverviewRapportViewerAccount" style={{marginLeft: (level * 5) + 'px'}}>

        <div
            className="OverviewRapportViewerAccountSummary"
        >
            <div className="OverviewRapportViewerAccountSummaryLevel">{!isLast && <MiddleLine/>}{isLast &&
                <LastLine/>}</div>
            <div className="OverviewRapportViewerAccountSummaryMain">
                <div className="OverviewRapportViewerAccountSummaryLeft">
                    {overviewRapportAccounts.length > 0 &&
                        <IconButton size={"small"} onClick={() => setLocalState(prev => ({
                            ...prev,
                            accountTree: prev.accountTree.toggle(account.accountId)
                        }))}>
                            {localState.accountTree.isExpanded(account.accountId) &&
                                <ArrowDropDownIcon fontSize={"small"}/>}
                            {!localState.accountTree.isExpanded(account.accountId) &&
                                <ArrowRightIcon fontSize={"small"}/>}
                        </IconButton>
                    }
                    {overviewRapportAccounts.length === 0 && <div style={{margin: '12px'}}></div>}
                    <div onClick={() => navigate('/bookings/' + account.accountId)}>
                        {accounts.getById(account.accountId)!.name}
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
                    level={level + 1}
                    isLast={index === overviewRapportAccounts.length - 1}
                    filter={filter}
                />))}
        </ul>}
    </li>)
}

const lineHeight = 110;
const lineWidth = 14;
const lineColor = "#696969"

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

