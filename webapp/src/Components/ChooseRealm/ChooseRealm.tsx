import {Button, Container} from "@mui/material";
import React from "react";
import {useGlobalState} from "../../utils/globalstate";
import {useNavigate} from "react-router-dom";


export const ChooseRealm = () => {
    const {userInfo} = useGlobalState();
    const {setUserStateV2, clearAccounts} = useGlobalState();

    const navigate = useNavigate()

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
            {userInfo.accessibleRealms.map(lv => (<div key={lv.id} style={{width: "80%"}}>
                <Button variant="contained" color="primary"
                        onClick={() => onRealmSelected(lv.id)}>{lv.name}</Button>
            </div>))}

        </div>

    </Container>);
}