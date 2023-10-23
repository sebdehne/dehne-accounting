import {v4 as uuidv4} from 'uuid';
import {RpcRequest, RpcResponse} from "./types/Rpc";
import {Notify, ReadRequest, ReadResponse} from "./types/Subscription";
import {WebsocketMessage} from "./types/WebsocketMessage";
import {sha256} from 'js-sha256';

export enum ConnectionStatus {
    connected = "connected",
    connectedAndWorking = "connectedAndWorking",
    connecting = "connecting",
    closed = "closed"
}

export type ConnectionStatusAndError = {
    status: ConnectionStatus;
    error?: string | undefined;
}


const OngoingRPCsById: { [K: string]: Rpc } = {}
const SubscriptionsById: { [K: string]: Subscription } = {};
const ConnectionStatusSubscriptionsById: { [K: string]: (s: ConnectionStatusAndError) => void } = {};
let ws: WebSocket | undefined = undefined;
let connectionStatusWithError: ConnectionStatusAndError = {
    status: ConnectionStatus.closed
};


const readCache: { [key: string]: ReadResponse } = {};

function subscribe(
    readRequest: ReadRequest,
    onNotify: (notify: Notify) => void) {

    const subscriptionId = uuidv4();

    SubscriptionsById[subscriptionId] = {readRequest, onNotify, initialNotifyReceived: false}

    rpc({
        type: "subscribe",
        subscribe: {subscriptionId, readRequest}
    });

    // proved cached data initially
    const cacheKey = sha256(JSON.stringify(readRequest));
    const cachedReadResponse = readCache[cacheKey];
    if (cachedReadResponse) {
        setTimeout(() => {
            onNotify({subscriptionId, readResponse: cachedReadResponse})
        }, 0);
    }

    return subscriptionId;
}

function unsubscribe(subscriptionId: string) {

    const sub = SubscriptionsById[subscriptionId];
    if (!sub) {
        return;
    }

    rpc({
        type: "unsubscribe",
        unsubscribe: {subscriptionId}
    }).then(() => {
        delete SubscriptionsById[subscriptionId];
    });

}

function rpc(rpcRequest: RpcRequest): Promise<RpcResponse> {
    const msgId = uuidv4();
    return new Promise<RpcResponse>((resolve, reject) => {
        const rpc = new Rpc(
            resolve,
            reject,
            {
                id: msgId,
                type: "rpcRequest",
                rpcRequest
            }
        )

        OngoingRPCsById[msgId] = rpc;

        send(rpc.msg);
    });
}

function send(msg: WebsocketMessage) {
    if (connectionStatusWithError.status === ConnectionStatus.closed) {
        reconnect();
    } else if (connectionStatusWithError.status === ConnectionStatus.connected || connectionStatusWithError.status === ConnectionStatus.connectedAndWorking) {
        try {
            ws?.send(JSON.stringify(msg));
        } catch (e) {
            console.log(e);
        }
    }
}

function setError(error: string | undefined) {
    connectionStatusWithError = ({
        ...connectionStatusWithError,
        error
    });
    Object.values(ConnectionStatusSubscriptionsById).forEach((value) => {
        value(connectionStatusWithError);
    });
}

function setConnectionStatusChanged(newState: ConnectionStatus) {
    const oldState = connectionStatusWithError.status;
    connectionStatusWithError = ({
        ...connectionStatusWithError,
        status: newState,
    });
    if (oldState !== newState) {
        Object.values(ConnectionStatusSubscriptionsById).forEach((value) => {
            value(connectionStatusWithError);
        });
    }
}

function reconnect() {
    setConnectionStatusChanged(ConnectionStatus.connecting);
    // @ts-ignore
    const urlTemplate: string = process.env.REACT_APP_WEBSOCKET_ENDPOINT;
    const wsUrl = urlTemplate.replace("HOST", window.location.host);
    console.log("Connecting to: " + wsUrl);

    ws = new WebSocket(wsUrl);
    ws.onopen = function () {
        setConnectionStatusChanged(ConnectionStatus.connected);
        // re-send ongoing RPCs
        Object.values(OngoingRPCsById).forEach((ongoingRPC) => {
            if (ongoingRPC.msg.rpcRequest?.type === "subscribe") {
                delete OngoingRPCsById[ongoingRPC.msg.id];
            } else if (ongoingRPC.msg.rpcRequest?.type === "unsubscribe") {
                delete OngoingRPCsById[ongoingRPC.msg.id];
            } else {
                console.log("onopen - re-send rpc: " + ongoingRPC.msg.id)
                send(ongoingRPC.msg);
            }
        });

        // re-subscribe
        Object.entries(SubscriptionsById).forEach(([key, sub]) => {
            console.log("onopen - re-subscribe: " + key)
            rpc({
                type: "subscribe",
                subscribe: {subscriptionId: key, readRequest: sub.readRequest}
            });
        })

    };
    ws.onmessage = function (e) {
        const json: WebsocketMessage = JSON.parse(e.data);

        if (json.type === "rpcResponse") {
            let ongoingRPC = OngoingRPCsById[json.id];
            if (ongoingRPC) {
                delete OngoingRPCsById[json.id];

                if (json.rpcResponse?.error) {
                    setError(
                        json.rpcResponse.error
                    )
                    //ongoingRPC.reject(json.rpcResponse.error)
                } else {
                    ongoingRPC.resolve(json.rpcResponse!!);
                }
            }
        } else if (json.type === "notify") {
            let notify = json.notify!!;
            const subId = notify.subscriptionId;
            let subscription = SubscriptionsById[subId];
            if (subscription) {

                const cacheKey = sha256(JSON.stringify(subscription.readRequest));
                readCache[cacheKey] = notify.readResponse;

                subscription.onNotify(notify);
                if (!subscription.initialNotifyReceived) {
                    subscription.initialNotifyReceived = true;
                }
            } else {
                console.log("Could not send notify - sub not found");
            }
        } else {
            console.log("Dont know what to do received websocket msg:");
            console.log(json);
        }

        const subsInitializing = Object.values(SubscriptionsById).filter((sub) => !sub.initialNotifyReceived).length;
        const rpcsInProgress = Object.entries(OngoingRPCsById).length

        if (subsInitializing + rpcsInProgress > 0) {
            setConnectionStatusChanged(ConnectionStatus.connectedAndWorking)
        } else {
            setConnectionStatusChanged(ConnectionStatus.connected)
        }
    };

    ws.onclose = function () {
        console.log("onClose received")
        setConnectionStatusChanged(ConnectionStatus.closed);
        setTimeout(function () {
            reconnect();
        }, 1000);
    };

    ws.onerror = (event) => {
        console.log("ERROR");
        console.log(event);
        ws?.close();
    };

    console.log("Finished configuring WebSocket");
}

const WebsocketService = {
    rpc,
    subscribe,
    unsubscribe,
    clearError: () => setError(undefined),
    monitorConnectionStatus: (fn: (status: ConnectionStatusAndError) => void): () => void => {
        const id = uuidv4();
        ConnectionStatusSubscriptionsById[id] = fn;
        fn(connectionStatusWithError);
        return () => {
            delete ConnectionStatusSubscriptionsById[id];
        };
    }
};


export default WebsocketService;

export type Subscription = {
    readRequest: ReadRequest;
    onNotify: (notify: Notify) => void;
    initialNotifyReceived: boolean;
}

class Rpc {
    public resolve: (rpcResponse: RpcResponse) => void;
    public reject: (error: string) => void;
    public msg: WebsocketMessage;

    public constructor(
        resolve: (rpcResponse: RpcResponse) => void,
        reject: (error: string) => void,
        msg: WebsocketMessage
    ) {
        this.reject = reject;
        this.resolve = resolve;
        this.msg = msg;
    }
}