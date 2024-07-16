import WebsocketService, {ConnectionStatus, ConnectionStatusAndError} from "../Websocket/websocketClient";
import {useEffect, useState} from "react";

export const Loading = () => {
    const [connectionStatusAndError, setConnectionStatusAndError] = useState<ConnectionStatusAndError>({
        status: ConnectionStatus.connecting
    });

    useEffect(() =>
        WebsocketService.monitorConnectionStatus((status: ConnectionStatusAndError) => {
            setConnectionStatusAndError(status);
        }), []);

    let displayStatus: string | undefined = undefined;

    if (connectionStatusAndError.status === ConnectionStatus.closed || connectionStatusAndError.status === ConnectionStatus.connecting) {
        displayStatus = "Server connection: " + connectionStatusAndError.status;
    } else if (connectionStatusAndError.status === ConnectionStatus.connectedAndWorking) {
        displayStatus = "working...";
    }

    return <div>Loading... {displayStatus}</div>
}