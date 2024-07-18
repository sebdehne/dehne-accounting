import {Container} from "@mui/material";
import Header from "../Header";
import React, {useEffect, useState} from "react";
import {useGlobalState} from "../../utils/globalstate";
import {Loading} from "../loading";
import {AccountSearchBox} from "../AccountSearchBox/AccountSearchBox";
import {useNavigate} from "react-router-dom";
import WebsocketClient from "../../Websocket/websocketClient";
import {AccountDto} from "../../Websocket/types/accounts";


export const BudgetRules = () => {
    const {realm, userInfo, accounts} = useGlobalState();
    const [budgetAccounts, setBudgetAccounts] = useState<AccountDto[]>([]);
    let navigate = useNavigate();

    useEffect(() => {
        if (realm) {
            const sub = WebsocketClient.subscribe({type: "getBudgetAccounts"}, readResponse => {

                let accountIds = readResponse.budgetAccounts!;
                setBudgetAccounts(accountIds.map(aId => accounts.getById(aId)!));
            })
            return () => WebsocketClient.unsubscribe(sub);
        }
    }, [realm, setBudgetAccounts]);

    if (!userInfo || !realm) return <Loading/>;

    const getName = (id: string) => accounts.generateParentsString(id)! + ":" + accounts.getById(id)!.name

    return (
        <Container maxWidth="xs" className="App">
            <Header
                title="Budget - choose account"
                userIsAdmin={userInfo.admin}
            />

            <div style={{margin: "20px"}}></div>

            <AccountSearchBox onSelectedAccountId={a => {
                if (a) {
                    navigate('/budget/' + a);
                }
            }} value={undefined}/>

            <ul style={{listStyle: "none", paddingLeft: 0}}>
                {budgetAccounts
                    .toSorted((a, b) => getName(a.id).localeCompare(getName(b.id)))
                    .map(a => (<li
                        key={a.id}
                        style={{margin: "5px"}}
                        onClick={() => navigate('/budget/' + a.id)}
                    >
                        {accounts.generateParentsString(a.id) + ":" + a.name}
                    </li>))}
            </ul>

        </Container>
    )
}
