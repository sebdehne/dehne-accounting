import {ButtonGroup, Container, TextField} from "@mui/material";
import Header from "../Header";
import {useGlobalState} from "../../utils/userstate";
import "./Accounts.css"
import {AccountExpanded} from "../../utils/accounts";
import IconButton from "@mui/material/IconButton";
import {ArrowDropDownIcon} from "@mui/x-date-pickers";
import ArrowRightIcon from "@mui/icons-material/ArrowRight";
import React, {useCallback, useState} from "react";
import {useNavigate} from "react-router-dom";
import CancelIcon from '@mui/icons-material/Cancel';
import EditIcon from "@mui/icons-material/Edit";
import MergeIcon from '@mui/icons-material/Merge';
import {useDialogs} from "../../utils/dialogs";
import WebsocketClient from "../../Websocket/websocketClient";
import AddIcon from "@mui/icons-material/Add";
import ListIcon from '@mui/icons-material/List';

export const Accounts = () => {
    const {accounts} = useGlobalState();
    const [filter, setFilter] = useState('');
    const [selectedAccountId, setSelectedAccountId] = useState('');

    return (<Container maxWidth="xs" className="App">
        <Header title={"Accounts"}/>

        <div style={{display: "flex", flexDirection: "row", justifyContent: "space-between"}}>
            <TextField
                fullWidth={true}
                label={"Filter"}
                value={filter}
                onChange={event => setFilter(event.target.value ?? '')}
            />
            {filter && <IconButton onClick={() => setFilter('')}><CancelIcon/></IconButton>}
        </div>

        <ul className="AccountsList">
            {accounts.tree.map(a => (<AccountViewer
                key={a.account.id}
                account={a}
                level={0}
                filter={filter}
                selectedAccountId={selectedAccountId}
                setSelectedAccountId={setSelectedAccountId}
            />))}
        </ul>
    </Container>)
}


type AccountViewerProps = {
    account: AccountExpanded;
    level: number;
    filter: string;
    selectedAccountId: string;
    setSelectedAccountId: React.Dispatch<React.SetStateAction<string>>;
}
const AccountViewer = ({account, level, filter, selectedAccountId, setSelectedAccountId}: AccountViewerProps) => {
    const {localState, setLocalState} = useGlobalState();
    const {showMergeAccountDialog} = useDialogs();

    const navigate = useNavigate();
    const children = account.filteredChildren(filter);

    const merge = useCallback(() => {
        showMergeAccountDialog({
            sourceAccountId: account.account.id,
            onConfirmed: targetAccountId => {
                WebsocketClient.rpc({
                    type: "mergeAccount",
                    accountId: account.account.id,
                    mergeTargetAccountId: targetAccountId,
                })
            }
        });
    }, [showMergeAccountDialog, account.account.id]);

    if (children === undefined) return null;

    return (<li>
        <div className="AccountSummary">
            {children.length > 0 &&
                <IconButton size={"small"} onClick={() => setLocalState(prev => ({
                    ...prev,
                    accountTree: prev.accountTree.toggle(account.account.id)
                }))}>
                    {(!!filter || localState.accountTree.isExpanded(account.account.id)) &&
                        <ArrowDropDownIcon fontSize={"small"}/>}
                    {(!filter && !localState.accountTree.isExpanded(account.account.id)) &&
                        <ArrowRightIcon fontSize={"small"}/>}
                </IconButton>
            }
            {children.length === 0 && <div style={{margin: '10px'}}></div>}

            <div
                onClick={() => setSelectedAccountId(prevState => prevState === account.account.id ? '' : account.account.id)}>
                {account.account.name}
            </div>

            {selectedAccountId === account.account.id && <ButtonGroup>
                <IconButton
                    onClick={() => navigate('/account?parentAccountId=' + account.account.id)}><AddIcon/></IconButton>
                <IconButton onClick={() => navigate('/account/' + account.account.id)}><EditIcon/></IconButton>
                {!account.account.builtIn && <IconButton onClick={merge}><MergeIcon/></IconButton>}
                <IconButton
                    onClick={() => navigate('/bookings/' + account.account.id)}><ListIcon/></IconButton>
            </ButtonGroup>}
        </div>

        {(!!filter || localState.accountTree.isExpanded(account.account.id)) && <ul className="AccountsList">
            {children.map(a => (
                <AccountViewer
                    key={a.account.id}
                    account={a}
                    level={level + 1}
                    filter={filter}
                    selectedAccountId={selectedAccountId}
                    setSelectedAccountId={setSelectedAccountId}
                />))}
        </ul>}
    </li>)
}