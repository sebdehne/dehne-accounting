import {Button, Container} from "@mui/material";
import React, {useEffect, useState} from "react";
import {Realm} from "../../Websocket/types/realm";
import WebsocketClient, {useUser} from "../../Websocket/websocketClient";
import {useGlobalState} from "../../utils/userstate";
import {useNavigate} from "react-router-dom";


export const ChooseRealm = () => {
    const [realms, setRealms] = useState<Realm[]>();
    const {user} = useUser();
    const {userStateV2, setUserStateV2} = useGlobalState();

    const navigate = useNavigate()

    useEffect(() => {
        const sub = WebsocketClient.subscribe(
            {type: "getAllRealms"},
            notify => setRealms(notify.readResponse.realms)
        );
        return () => WebsocketClient.unsubscribe(sub);
    }, []);

    const openLedger = (realmId: string) => {
        setUserStateV2(prev => ({
            ...prev,
            selectedRealm: realmId
        })).then(() => navigate('/'));
    }

    return (<Container maxWidth="xs" className="App">
        {user && <h4>Welcome {user.name}</h4>}

        <div
            style={{
                display: "flex",
                flexDirection: "column"
            }}
        >
            {realms?.map(lv => (<div key={lv.id} style={{width: "80%"}}>
                <Button variant="contained" color="primary"
                        onClick={() => openLedger(lv.id)}>{lv.name}</Button>
            </div>))}

        </div>

    </Container>);
}