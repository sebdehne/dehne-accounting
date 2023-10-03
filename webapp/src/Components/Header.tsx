import React, {useEffect, useState} from "react";
import WebsocketService, {ConnectionStatus} from "../Websocket/websocketClient";
import {Button, CircularProgress} from "@mui/material";
import {useNavigate} from "react-router-dom";
import ArrowBackIcon from '@mui/icons-material/ArrowBack';

type HeaderProps = {
    title: string;
    suppressHome?: boolean;
    temporaryPage?: boolean;
}

const Header = ({title, suppressHome = false}: HeaderProps) => {
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

    const goBack = () => {
        navigate(-1);
    }

    return <>
        <div style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between"
        }
        }>
            <div style={{
                display: "flex",
                flexDirection: "row"
            }}>
                {!suppressHome && <Button style={{marginRight: '5px'}} color="primary" variant="contained"
                                          onClick={() => navigate('/')}>Home</Button>
                }
                <Button color="primary" variant="contained" onClick={goBack}><ArrowBackIcon/>Back</Button>
            </div>
            <div>
                {status === ConnectionStatus.connectedAndWorking &&
                    <CircularProgress color="primary"/>
                }
                {displayStatus && <span>Server connection: {displayStatus}</span>}
            </div>

        </div>
        <h2 style={{textAlign: "center"}}>{title}</h2>

    </>
};

export default Header;