import Header from "../Header";
import React, {useCallback, useEffect, useState} from "react";
import {
    Accordion,
    AccordionDetails,
    AccordionSummary,
    Button,
    ButtonGroup,
    Checkbox,
    Container,
    FormControlLabel,
    TextField
} from "@mui/material";
import {RealmAccessLevel, User} from "../../Websocket/types/User";
import WebsocketClient from "../../Websocket/websocketClient";
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import {InformationElement} from "../../Websocket/types/InformationElement";
import SaveIcon from "@mui/icons-material/Save";
import CancelIcon from "@mui/icons-material/Cancel";

const clone = (u: User) => JSON.parse(JSON.stringify(u)) as User;

export const UserManagement = () => {
    const [users, setUsers] = useState<User[]>([]);
    const [usersOriginal, setUsersOriginal] = useState<User[]>([]);
    const [allRealms, setAllRealms] = useState<InformationElement[]>([]);

    useEffect(() => {
        const subId = WebsocketClient.subscribe(
            {type: "getAllUsers"},
            readResponse => {
                setUsersOriginal(readResponse.allUsers!.map(c => clone(c)));
                setUsers(readResponse.allUsers!);
                setAllRealms(readResponse.realms!);
            }
        )
        return () => WebsocketClient.unsubscribe(subId);
    }, [setUsers]);

    return (
        <Container maxWidth="xs" className="App">
            <Header
                title={"User management"}
            />

            <ul style={{listStyle: "none", paddingLeft: "0"}}>
                {users.map(u => (<UserComponent
                    key={u.id}
                    user={u}
                    realms={allRealms}
                    setUser={fn => setUsers(prevState => {

                        const current = prevState.find(c => c.id === u.id)!;
                        const updated = fn(current);

                        return (prevState.map(c => c.id === u.id ? updated : c))
                    })}
                    hasChanges={JSON.stringify(usersOriginal.find(c => c.id === u.id)) !== JSON.stringify(u)}
                    forgetChanges={() => setUsers(prevState =>
                        (prevState.map(c => c.id === u.id ? clone(usersOriginal.find(u2 => u2.id === u.id)!) : c)))}
                />))}
            </ul>

        </Container>
    )
}

type UserProps = {
    user: User;
    realms: InformationElement[];
    setUser: (fn: (previous: User) => User) => void;
    hasChanges: boolean;
    forgetChanges: () => void;
}
const UserComponent = ({user, realms, setUser, hasChanges, forgetChanges}: UserProps) => {

    console.log(user);

    const levelIs = useCallback((realmId: string, l: RealmAccessLevel) =>
            user.realmIdToAccessLevel[realmId] === l,
        [user, realms]);

    const toggleAccess = useCallback((realmId: string, l: RealmAccessLevel) => {
        const currentLevel = user.realmIdToAccessLevel[realmId];
        if (currentLevel === l) {
            setUser(prevState => ({
                ...prevState,
                realmIdToAccessLevel: Object.fromEntries(Object.entries(prevState.realmIdToAccessLevel).filter(([k, v]) => k !== realmId))
            }))
        } else {
            setUser(prevState => ({
                ...prevState,
                realmIdToAccessLevel: {
                    ...prevState.realmIdToAccessLevel,
                    [realmId]: l
                }
            }))
        }
    }, [setUser, user]);

    console.log(user);

    const save = useCallback(() => {
        WebsocketClient.rpc({
            type: "addOrReplaceUser",
            user,
        })
    }, [user]);

    return (<Accordion>
        <AccordionSummary
            expandIcon={<ExpandMoreIcon/>}
        >
            {user.userEmail}
        </AccordionSummary>
        <AccordionDetails>
            <div>
                <TextField
                    label={"Name"}
                    value={user.name}
                    fullWidth={true}
                    onChange={event => setUser(prevState => ({
                        ...prevState,
                        name: event.target.value ?? ""
                    }))}
                ></TextField>
                <TextField
                    label={"Description"}
                    value={user.description ?? ""}
                    fullWidth={true}
                    onChange={event => setUser(prevState => ({
                        ...prevState,
                        description: event.target.value ?? ""
                    }))}
                ></TextField>
                <div>
                    <FormControlLabel
                        label="Active?"
                        control={<Checkbox
                            checked={user.active}
                            onChange={(event, checked) => setUser(previous => ({
                                ...previous,
                                active: checked
                            }))}
                        />}
                    />
                    <FormControlLabel
                        label="Admin?"
                        control={<Checkbox
                            checked={user.admin}
                            onChange={(event, checked) => setUser(previous => ({
                                ...previous,
                                admin: checked
                            }))}
                        />}
                    />
                </div>
                <div>
                    <ul style={{listStyle: "none", paddingLeft: "0"}}>
                        {realms.map(r => (<li
                            style={{
                                display: "flex",
                                flexDirection: "row",
                                alignItems: "center",
                                justifyContent: "space-between"
                            }}
                            key={r.id}
                        >
                            <span>{r.name}</span>
                            <ButtonGroup>
                                <Button
                                    variant={levelIs(r.id, "read") ? "contained" : "outlined"}
                                    onClick={() => toggleAccess(r.id, "read")}
                                >read</Button>
                                <Button
                                    variant={levelIs(r.id, "readWrite") ? "contained" : "outlined"}
                                    onClick={() => toggleAccess(r.id, "readWrite")}
                                >readWrite</Button>
                                <Button
                                    variant={levelIs(r.id, "owner") ? "contained" : "outlined"}
                                    onClick={() => toggleAccess(r.id, "owner")}
                                >owner</Button>
                            </ButtonGroup>
                        </li>))}
                    </ul>
                </div>

                <div style={{
                    width: "100%",
                    display: "flex",
                    flexDirection: "row",
                    justifyContent: "flex-end",
                    paddingTop: "20px"
                }}>
                    <Button
                        disabled={!hasChanges}
                        onClick={forgetChanges}
                    >
                        <CancelIcon/>
                    </Button>
                    <Button
                        disabled={!hasChanges}
                        onClick={save}
                    >
                        <SaveIcon/>
                    </Button>
                </div>


            </div>
        </AccordionDetails>
    </Accordion>)
}

// name
// description
// active / inactive
// admin / not admin
// realm access: read, write owner
