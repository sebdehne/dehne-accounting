import {Button, Container, FormControl, InputLabel, MenuItem, Select, TextField} from "@mui/material";
import Header from "../Header";
import {useGlobalState} from "../../utils/globalstate";
import {useNavigate, useParams, useSearchParams} from "react-router-dom";
import "./Account.css"
import {useEffect, useState} from "react";
import {AccountDto} from "../../Websocket/types/accounts";
import {v4 as uuidv4} from "uuid";
import {AccountSearchBox} from "../AccountSearchBox/AccountSearchBox";
import WebsocketClient from "../../Websocket/websocketClient";
import {Loading} from "../loading";
import {BudgetType} from "../../Websocket/types/budget";

export const Account = () => {
    const {accountId} = useParams();
    let [searchParams] = useSearchParams();
    const {accounts} = useGlobalState();
    const [account, setAccount] = useState<AccountDto>({
        builtIn: false,
        parentAccountId: searchParams.get('parentAccountId') ?? '',
        name: "",
        partyId: undefined,
        id: accountId ?? uuidv4(),
        realmId: "",
        budgetType: "min",
    });

    const accountsHasData = accounts.hasData();

    useEffect(() => {
        if (accountsHasData && accountId) {
            setAccount(accounts.getById(accountId)!)
        }
    }, [accountsHasData, accountId, accounts]);

    const navigate = useNavigate();

    if (!accountsHasData) return <Loading/>;

    const isValid = !!account.name && !!account.parentAccountId

    const submit = () => {
        if (isValid) {
            WebsocketClient.rpc({
                type: "createOrUpdateAccount",
                createOrUpdateAccount: account
            }).then(() => navigate(-1))
        }
    }

    return (<Container maxWidth="xs" className="App">
        <Header title={accountId ? "Edit account" : "Create new account"}/>

        <TextField
            value={account.name}
            onChange={event => setAccount(prevState => ({
                ...prevState,
                name: event.target.value ?? ''
            }))}
            label={"Name"}
            fullWidth={true}
        />

        <div style={{margin: "10px"}}></div>

        <FormControl fullWidth>
            <InputLabel id="budgetType-select-label">Budget type</InputLabel>
            <Select
                labelId="budgetType-select-label"
                id="budgetType-select"
                value={account.budgetType ?? ''}
                label="Budget type"
                onChange={event => {
                    const v = event.target.value;
                    if (v) {
                        setAccount(prevState => ({
                            ...prevState,
                            budgetType: v as BudgetType
                        }));
                    } else {
                        setAccount(prevState => ({
                            ...prevState,
                            budgetType: undefined
                        }));
                    }
                }}
            >
                <MenuItem value={''}>None</MenuItem>
                <MenuItem value={'min'}>Reach at least</MenuItem>
                <MenuItem value={'max'}>Stay below</MenuItem>
            </Select>
        </FormControl>

        <div style={{margin: "10px"}}></div>

        {!account.builtIn && <AccountSearchBox
            onSelectedAccountId={accountId1 => {
                if (accountId1) {
                    setAccount(prevState => ({
                        ...prevState,
                        parentAccountId: accountId1
                    }));
                }
            }}
            value={account.parentAccountId}
            title={"Parent account"}
        />}

        <div style={{marginTop: "20px"}}>
            <Button
                variant={"contained"}
                onClick={submit}
                disabled={!isValid}
            >Submit</Button>
        </div>

    </Container>)
}

