import {Button, Container} from "@mui/material";
import React, {useEffect, useState} from "react";
import {LedgerView} from "../../Websocket/types/ledgers";
import WebsocketService, {useUser} from "../../Websocket/websocketClient";
import {useGlobalState} from "../../utils/userstate";
import {useNavigate} from "react-router-dom";


export const ChooseLedger = () => {
    const [ledgers, setLedgers] = useState<LedgerView[]>();
    const {user} = useUser();
    const {userState, setUserState} = useGlobalState();

    useEffect(() => {
        const subId = WebsocketService.subscribe(
            {type: "getLedgers"},
            n => setLedgers(n.readResponse.ledgers)
        );

        return () => WebsocketService.unsubscribe(subId);
    }, [setLedgers]);

    let navigate = useNavigate();

    useEffect(() => {
        if (userState?.ledgerId) {
            navigate('/')
        }
    }, [userState, navigate]);

    const openLedger = (ledgerId: string) => {
        setUserState(prev => ({
            ...prev,
            ledgerId,
        }))
    }

    return (<Container maxWidth="xs" className="App">
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
    </Container>)
}