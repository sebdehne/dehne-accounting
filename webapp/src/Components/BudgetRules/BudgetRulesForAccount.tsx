import {useGlobalState} from "../../utils/globalstate";
import {useNavigate, useParams} from "react-router-dom";
import {Loading} from "../loading";
import {Button, Container} from "@mui/material";
import Header from "../Header";
import React, {useEffect, useState} from "react";
import {BudgetRule} from "../../Websocket/types/budget";
import WebsocketClient from "../../Websocket/websocketClient";
import {AmountTextField} from "../AmountTextfield/AmountTextfield";
import SaveIcon from "@mui/icons-material/Save";
import DeleteIcon from "@mui/icons-material/Delete";


export const BudgetRulesForAccount = () => {
    const {realm, userInfo, accounts} = useGlobalState();
    let navigate = useNavigate();
    const {accountId} = useParams();
    const [budgetRules, setBudgetRules] = useState<BudgetRule[]>([]);
    const [changed, setChanged] = useState(false);

    useEffect(() => {
        if (!accountId) {
            navigate('/budget')
        } else {
            WebsocketClient.subscribe({
                type: "getBudgetRulesForAccount",
                accountId,
            }, readResponse => {
                setBudgetRules(readResponse.budgetRules!);
                setChanged(false);
            })
        }
    }, [accountId, setChanged, setBudgetRules]);

    if (!userInfo || !realm || !accountId) return <Loading/>;

    let account = accounts.getById(accountId)!;
    const name = accounts.generateParentsString(accountId);

    const monthIndexes = [...Array(12).keys()];
    const getAmount = (monthIndex: number) => {
        let budgetRule = budgetRules.find(r => r.month === monthIndex + 1);
        return budgetRule?.amountInCents ?? undefined;
    };
    const setAmount = (monthIndex: number, amount: number | undefined) => {
        if (typeof (amount) === "number") {
            setBudgetRules(prevState => [
                ...prevState.filter(r => r.month !== monthIndex + 1),
                {
                    month: monthIndex + 1,
                    amountInCents: amount,
                    accountId,
                    realmId: realm.id
                }
            ]);
        } else {
            setBudgetRules(prevState => [
                ...prevState.filter(r => r.month !== monthIndex + 1),
            ]);
        }
        setChanged(true);
    }

    const save = () => {
        WebsocketClient.rpc({
            type: "updateBudgetRulesForAccount",
            updateBudget: {
                accountId: accountId,
                budget: Object.fromEntries(budgetRules.map(r => ([r.month, r.amountInCents])))
            },
        });
    }

    return (
        <Container maxWidth="xs" className="App">
            <Header
                title={'Budget for ' + account.name}
                userIsAdmin={userInfo.admin}
                subTitle={name}
            />

            <ul style={{listStyle: "none", paddingLeft: 0}}>
                {monthIndexes.map(monthIndex => (
                    <Month key={monthIndex}
                           monthIndex={monthIndex}
                           amount={getAmount(monthIndex)}
                           setValue={newValue => setAmount(monthIndex, newValue)}
                    />
                ))}
            </ul>

            <Button onClick={save} disabled={!changed}> <SaveIcon/> Save</Button>
            <Button onClick={() => {
                setBudgetRules([]);
                setChanged(budgetRules.length > 0);
            }}> <DeleteIcon/> Clear all</Button>

        </Container>
    )
}

const Month = ({amount, setValue, monthIndex}: {
    monthIndex: number;
    amount: number | undefined;
    setValue: (newValue: number | undefined) => void;
}) => {

    const date = new Date(2009, monthIndex, 1);
    const monthName = date.toLocaleString('default', {month: 'long'});

    return (
        <li style={{marginTop: "10px"}}>
            <AmountTextField label={monthName} initialValue={amount} setValue={setValue} allowEmpty={true}/>
        </li>
    )
}