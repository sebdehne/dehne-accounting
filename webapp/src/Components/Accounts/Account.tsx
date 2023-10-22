import {Button, Container, TextField} from "@mui/material";
import Header from "../Header";
import {useGlobalState} from "../../utils/userstate";
import {useNavigate, useParams} from "react-router-dom";
import "./Account.css"
import {useEffect, useState} from "react";
import {AccountDto} from "../../Websocket/types/accounts";
import {v4 as uuidv4} from "uuid";
import {AccountSearchBox} from "../AccountSearchBox/AccountSearchBox";
import WebsocketClient from "../../Websocket/websocketClient";

export const Account = () => {
    const {accountId} = useParams();
    const {accounts} = useGlobalState();
    const [account, setAccount] = useState<AccountDto>({
        builtIn: false,
        parentAccountId: '',
        name: "",
        partyId: undefined,
        id: accountId ?? uuidv4(),
    });

    useEffect(() => {
        if (accounts.hasData() && accountId) {
            setAccount(accounts.getById(accountId))
        }
    }, [accounts.hasData(), accountId]);

    const navigate = useNavigate();

    if (!accounts.hasData()) return null;

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

        <AccountSearchBox
            onSelectedAccountId={accountId1 => {
                if (accountId1) {
                    setAccount(prevState => ({
                        ...prevState,
                        parentAccountId: accountId
                    }));
                }
            }}
            value={account.parentAccountId}
            title={"Parent account"}
        />

        <div style={{marginTop: "20px"}}>
            <Button
                variant={"contained"}
                onClick={submit}
                disabled={!isValid}
            >Submit</Button>
        </div>

    </Container>)
}

