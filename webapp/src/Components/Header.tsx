import React, {useEffect, useState} from "react";
import WebsocketService, {ConnectionStatus} from "../Websocket/websocketClient";
import {Button, ButtonGroup, CircularProgress} from "@mui/material";
import {useNavigate} from "react-router-dom";
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import "./Header.css"

type HeaderProps = {
    title: string;
    clickable?: () => void;
}

const Header = ({title, clickable}: HeaderProps) => {
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

    return <div className="Header">
        <div className="HeaderButtons">
            <div className="HeaderButtonsLeft">
                <Button
                    size={"small"}
                    color="primary" variant="contained" onClick={goBack}><ArrowBackIcon/>Back</Button>
            </div>
            <div className="HeaderButtonsRight">
                <ButtonGroup>
                    <Button
                    size={"small"}
                        style={{marginRight: '2px'}}
                            color="primary"
                            variant="contained"
                            onClick={() => navigate('/')}>Home</Button>
                    <Button
                        size={"small"}
                        style={{marginRight: '2px'}}
                            color="primary"
                            variant="contained"
                            onClick={() => navigate('/bookings')}>Bookings</Button>
                    <Button
                        size={"small"}
                        style={{marginRight: '2px'}}
                            color="primary"
                            variant="contained"
                            onClick={() => navigate('/categories')}>Categories</Button>
                </ButtonGroup>
            </div>
        </div>
        <div className="HeaderConnectionStatus">
            {status === ConnectionStatus.connectedAndWorking &&
                <CircularProgress color="primary"/>
            }
            {displayStatus && <span>Server connection: {displayStatus}</span>}
        </div>

        {clickable && <h2 className="HeaderAsLink" onClick={clickable}>{title}</h2>}
        {!clickable && <h2 className="HeaderNoLink">{title}</h2>}

    </div>
};

export default Header;