import {Button, Container} from "@mui/material";
import React from "react";
import {useGlobalState} from "../../utils/globalstate";
import {useNavigate} from "react-router-dom";


export const ChooseRealm = () => {
    const {userInfo} = useGlobalState();
    const {setUserStateV2} = useGlobalState();

    const navigate = useNavigate()

    const onRealmSelected = (realmId: string) => {
        setUserStateV2(prev => ({
            ...prev,
            selectedRealm: realmId
        })).then(() => {
            navigate('/');
        });
    }

    if (!userInfo) return null;

    return (<Container maxWidth="xs" className="App">
        <div
            style={{
                display: "flex",
                flexDirection: "column"
            }}
        >
            {Object.entries(userInfo.realmIdToAccessLevel).map(([realmId, ral]) => (<div key={realmId} style={{width: "80%"}}>
                <Button variant="contained" color="primary"
                        onClick={() => onRealmSelected(realmId)}>{ral}</Button>
            </div>))}

        </div>

    </Container>);
}