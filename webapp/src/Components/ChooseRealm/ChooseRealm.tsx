import {Button, Container} from "@mui/material";
import React, {useEffect, useState} from "react";
import {Realm} from "../../Websocket/types/realm";
import WebsocketClient from "../../Websocket/websocketClient";
import {useGlobalState} from "../../utils/userstate";
import {useNavigate} from "react-router-dom";


export const ChooseRealm = () => {
    const [realms, setRealms] = useState<Realm[]>();
    const {setUserStateV2, clearAccounts} = useGlobalState();

    const navigate = useNavigate()

    useEffect(() => {
        const sub = WebsocketClient.subscribe(
            {type: "getAllRealms"},
            readResponse => setRealms(readResponse.realms)
        );
        return () => WebsocketClient.unsubscribe(sub);
    }, []);

    const onRealmSelected = (realmId: string) => {
        clearAccounts();
        setUserStateV2(prev => ({
            ...prev,
            selectedRealm: realmId
        })).then(() => {
            navigate('/');
        });
    }

    return (<Container maxWidth="xs" className="App">
        <div
            style={{
                display: "flex",
                flexDirection: "column"
            }}
        >
            {realms?.map(lv => (<div key={lv.id} style={{width: "80%"}}>
                <Button variant="contained" color="primary"
                        onClick={() => onRealmSelected(lv.id)}>{lv.name}</Button>
            </div>))}

        </div>

    </Container>);
}