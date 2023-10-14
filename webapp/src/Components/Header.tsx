import React, {useEffect, useState} from "react";
import WebsocketService, {ConnectionStatus} from "../Websocket/websocketClient";
import {Button, ButtonGroup, CircularProgress, Divider, Menu, MenuItem, MenuList} from "@mui/material";
import {useNavigate} from "react-router-dom";
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import "./Header.css"

type HeaderProps = {
    title: string;
    clickable?: () => void;
    extraMenuOptions?: [string, string][];
}

const Header = ({title, clickable, extraMenuOptions}: HeaderProps) => {
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
                <BasicMenu extraMenuOptions={extraMenuOptions ?? []}/>
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


type BasicMenuProps = {
    extraMenuOptions: [string, string][];
}
const BasicMenu = ({extraMenuOptions}: BasicMenuProps) => {
    const [anchorEl, setAnchorEl] = React.useState<null | HTMLElement>(null);
    const open = Boolean(anchorEl);
    const handleClick = (event: React.MouseEvent<HTMLButtonElement>) => {
        setAnchorEl(event.currentTarget);
    };
    const navigate = useNavigate();
    const handleClose = (url?: string) => {
        setAnchorEl(null);
        if (url) {
            navigate(url);
        }
    };

    return (
        <div>
            <Button
                id="basic-button"
                aria-controls={open ? 'basic-menu' : undefined}
                aria-haspopup="true"
                aria-expanded={open ? 'true' : undefined}
                onClick={handleClick}
                color="primary" variant="contained"
            >
                Menu
            </Button>
            <Menu
                id="basic-menu"
                anchorEl={anchorEl}
                open={open}
                onClose={() => handleClose()}
                MenuListProps={{
                    'aria-labelledby': 'basic-button',
                }}
            >
                <MenuItem onClick={() => handleClose('/')}>Home</MenuItem>
                <MenuItem onClick={() => handleClose('/bankaccounts')}>Bank accounts</MenuItem>
                {extraMenuOptions.length > 0 && <MenuList>
                    <Divider />
                    {extraMenuOptions.map(([name, link]) => (
                        <MenuItem key={name} onClick={() => handleClose(link)}>{name}</MenuItem>
                    ))}
                </MenuList>}
            </Menu>
        </div>
    );
}