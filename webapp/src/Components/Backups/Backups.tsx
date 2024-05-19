import Header from "../Header";
import React, {useEffect, useState} from "react";
import {Accordion, AccordionDetails, AccordionSummary, Button, Container} from "@mui/material";
import WebsocketClient from "../../Websocket/websocketClient";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import DeleteIcon from "@mui/icons-material/Delete";
import IconButton from "@mui/material/IconButton";
import {useDialogs} from "../../utils/dialogs";

export const Backups = () => {
    const [backups, setBackups] = useState<string[]>([]);

    useEffect(() => {
        const sub = WebsocketClient.subscribe({
            type: "listBackups"
        }, readResponse => setBackups(readResponse.backups!!));
        return () => WebsocketClient.unsubscribe(sub);
    }, []);

    const {showConfirmationDialog} = useDialogs();

    const deleteBackup = (name: string) => {
        if (name) {
            showConfirmationDialog({
                header: "Delete backup?",
                content: "Are you sure? This cannot be undone",
                onConfirmed: () => {
                    WebsocketClient.rpc({
                        type: "dropBackup",
                        backupName: name
                    })
                }
            })
        }
    }

    const restoreBackup = (name: string) => {
        if (name) {
            showConfirmationDialog({
                header: "Restore backup ?",
                content: "Are you sure? This cannot be undone",
                onConfirmed: () => {
                    WebsocketClient.rpc({
                        type: "restoreBackup",
                        backupName: name
                    })
                }
            })
        }
    }


    return (
        <Container maxWidth="xs" className="App">
            <Header
                title={"Backups"}
            />

            <div style={{display: "flex", flexDirection: "row", justifyContent: "space-around"}}>
                <Button
                    variant={"contained"}
                    onClick={() => {
                        WebsocketClient.rpc({type: "createNewBackup"})
                    }}
                >Create new backup</Button>
            </div>

            <ul style={{listStyle: "none", paddingLeft: "0"}}>
                {backups.map(value => (<Accordion key={value}>
                    <AccordionSummary
                        expandIcon={<ExpandMoreIcon/>}
                    >
                        {value}
                    </AccordionSummary>
                    <AccordionDetails>
                        <div>
                            <Button onClick={() => restoreBackup(value)}>Restore</Button>
                            <IconButton onClick={() => deleteBackup(value)}><DeleteIcon/></IconButton>
                        </div>
                    </AccordionDetails>
                </Accordion>))}
            </ul>

        </Container>
    )
}