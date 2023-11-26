import Header from "../Header";
import React, {useEffect, useState} from "react";
import {Container} from "@mui/material";
import {User} from "../../Websocket/types/User";
import WebsocketClient from "../../Websocket/websocketClient";

export const UserManagement = () => {
    const [users, setUsers] = useState<User[]>([]);

    useEffect(() => {
        const subId = WebsocketClient.subscribe(
            {type: "getAllUsers"},
            readResponse => setUsers(readResponse.allUsers!)
        )
        return () => WebsocketClient.unsubscribe(subId);
    }, [setUsers]);

    return (
        <Container maxWidth="xs" className="App">
            <Header
                title={"User management"}
            />

            <ul>
                {users.map(u => (<li key={u.id}>{u.userEmail}</li>))}
            </ul>

        </Container>
    )
}

// name
// description
// active / inactive
// admin / not admin
// realm access: read, write owner
