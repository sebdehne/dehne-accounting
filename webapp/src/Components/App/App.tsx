import React, {useEffect, useState} from 'react';
import './App.css';
import {useNavigate} from "react-router-dom";
import {Button, Container} from "@mui/material";
import Header from "../Header";
import {LedgerView} from "../../Websocket/types/ledgers";
import WebsocketService, {useUser} from "../../Websocket/websocketClient";
import {useUserState} from "../../utils/userstate";


function App() {

    const navigate = useNavigate();

    const {user} = useUser()
    const [ledgers, setLedgers] = useState<LedgerView[]>();
    const {userState, setUserState} = useUserState();

    useEffect(() => {
        const subId = WebsocketService.subscribe(
            {type: "getLedgers"},
            n => setLedgers(n.readResponse.ledgers)
        );

        return () => WebsocketService.unsubscribe(subId);
    }, [setLedgers]);

    const openLedger = (ledgerId: string) => {
        setUserState(prev => ({
            ...prev,
            ledgerId
        })).then(() => navigate('/ledger'))
    }

    return (
        <Container maxWidth="sm" className="App">
            <Header
                title="Dehne Accounting"
                suppressHome={true}
            />

            {user && <h4>Welcome {user.name}</h4>}

            <div
                style={{
                    display: "flex",
                    flexDirection: "column"
                }}
            >
                {ledgers?.map(lv => (<div key={lv.id} style={{width: "80%"}}>
                    <Button variant="contained" color="primary"
                            onClick={() => openLedger(lv.id)}>{lv.name}</Button>
                </div>))}

            </div>
        </Container>
    );
}

export default App;
