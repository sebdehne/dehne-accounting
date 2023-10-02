import React, {useEffect, useState} from "react";
import WebsocketService, {ConnectionStatus} from "../Websocket/websocketClient";
import {Button, CircularProgress} from "@mui/material";
import {useNavigate} from "react-router-dom";
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import {useUserState} from "../utils/userstate";

type HeaderProps = {
    title: string;
    backUrl?: string;
    backName?: string;
}

const Header = ({title, backUrl, backName}: HeaderProps) => {
    const [status, setStatus] = useState<ConnectionStatus>(ConnectionStatus.connecting);
    const {userState, setUserState} = useUserState();
    const navigate = useNavigate();

    useEffect(() =>
        WebsocketService.monitorConnectionStatus((status: ConnectionStatus) => {
            setStatus(status);
        }), []);

    let displayStatus: string | undefined = undefined;
    if (status === ConnectionStatus.closed || status === ConnectionStatus.connecting) {
        displayStatus = status;
    }

    const canGoBack = !!userState.backUrl || !!backUrl;

    const goBack = () => {
        if (userState.backUrl) {
            const bUrl = userState.backUrl;
            setUserState(prev => ({...prev, backUrl: undefined})).then(() => navigate(bUrl));
        } else if (backUrl) {
            navigate(backUrl);
        }
    }

    return <>
        <div style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between"
        }
        }>
            {canGoBack &&
                <Button color="primary" variant="contained" onClick={goBack}><ArrowBackIcon/>{backName}
                </Button>
            }
            {!canGoBack && <span>&nbsp;</span>}
            {status === ConnectionStatus.connectedAndWorking &&
                <CircularProgress color="primary"/>
            }
            {displayStatus && <span>Server connection: {displayStatus}</span>}

        </div>
        <h2 style={{textAlign: "center"}}>{title}</h2>

    </>
};

export default Header;