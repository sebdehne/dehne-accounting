import React, {useEffect, useState} from "react";
import WebsocketService, {ConnectionStatus} from "../Websocket/websocketClient";
import {Button, CircularProgress} from "@mui/material";
import {useNavigate} from "react-router-dom";
import ArrowBackIcon from '@mui/icons-material/ArrowBack';

type HeaderProps = {
    title: string;
    backUrl?: string;
    backName?: string;
}

const Header = ({title, backUrl, backName}: HeaderProps) => {
    const [status, setStatus] = useState<ConnectionStatus>(ConnectionStatus.connecting);
    const navigate = useNavigate();

    useEffect(() =>
        WebsocketService.monitorConnectionStatus((status: ConnectionStatus) => {
            setStatus(status);
        }), []);

    let displayStatus: string | undefined = undefined;
    if (status === ConnectionStatus.closed || status === ConnectionStatus.connecting) {
        displayStatus = status;
    }

    return <>
        <div style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between"
        }
        }>
            {backUrl &&
                <Button color="primary" variant="contained" onClick={() => navigate(backUrl)}><ArrowBackIcon/>{backName}
                </Button>
            }
            {!backUrl && <span>&nbsp;</span>}
            {status === ConnectionStatus.connectedAndWorking &&
                <CircularProgress color="primary"/>
            }
            {displayStatus && <span>Server connection: {displayStatus}</span>}

        </div>
        <h2 style={{textAlign: "center"}}>{title}</h2>

    </>
};

export default Header;