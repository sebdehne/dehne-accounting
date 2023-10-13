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
import {toColor} from "../../utils/formatting";

export const RealmMain = () => {
    const {userStateV2, setUserStateV2, realm} = useGlobalState();
    const [overviewRapport, setOverviewRapport] = useState<OverviewRapportAccount[]>();

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


    return (
        <Container maxWidth="xs" className="App">
            <Header
                title={"Realm: " + realm?.name ?? ""}
                clickable={onHeaderClick}
            />

            <PeriodSelectorV2/>

            {overviewRapport && <OverviewRapportViewer overviewRapport={overviewRapport}/>}

        </Container>
    );
}


type OverviewRapportViewerProps = {
    overviewRapport: OverviewRapportAccount[]
}
const OverviewRapportViewer = ({overviewRapport}: OverviewRapportViewerProps) => {

    return (<div style={{paddingTop: '50px'}}>

        <LabeledNumberRow
            label={""}
            first={<div style={{fontWeight: 'bold'}}>Open balance</div>}
            second={<div style={{fontWeight: 'bold'}}>This period</div>}
            onClick={() => {}}
        />

        <ul className="OverviewRapportViewerAccounts">
            {overviewRapport.map(a => (<OverviewRapportViewerAccount
                key={a.name}
                account={a}
                showOpen={a.name !== "Expense" && a.name !== "Income"}
                level={0}
            />))}
        </ul>
    </div>)
}


type OverviewRapportViewerAccountProps = {
    account: OverviewRapportAccount;
    showOpen: boolean;
    level: number;
}
const OverviewRapportViewerAccount = ({account, showOpen, level}: OverviewRapportViewerAccountProps) => {
    const [expanded, setExpanded] = useState(false);

    return (<li className="OverviewRapportViewerAccount" style={(expanded && account.children.length > 0) ? {backgroundColor: toColor(2960941 + (level * 50))} : {}}>
        <LabeledNumberRow
            label={account.name}
            first={showOpen && <Amount amountInCents={account.openBalance}/>}
            second={<Amount amountInCents={account.thisPeriod}/>}
            onClick={() => setExpanded(!expanded)}
        />

        {expanded && <ul className="OverviewRapportViewerAccounts">
            {account.children
                .filter(c => showOpen || c.thisPeriod > 0)
                .map(c => (<OverviewRapportViewerAccount
                    key={c.name}
                    account={c}
                    showOpen={showOpen}
                    level={level + 1}
                />))}
        </ul>}
    </li>)
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

