import {Button, Container, FormControl, InputLabel, MenuItem, Select, TextField} from "@mui/material";
import Header from "../Header";
import {useNavigate, useParams} from "react-router-dom";
import {useEffect, useState} from "react";
import WebsocketClient from "../../Websocket/websocketClient";
import {BankAccount, BankWithAccounts} from "../../Websocket/types/bankaccount";
import dayjs from "dayjs";
import {useGlobalState} from "../../utils/userstate";
import {AccountSearchBox} from "../AccountSearchBox/AccountSearchBox";
import {DatePicker} from "@mui/x-date-pickers";
import {formatIso} from "../../utils/formatting";


export const AddOrReplaceBankAccount = () => {
    const {accountId} = useParams();
    const [banksAndAccounts, setBanksAndAccounts] = useState<BankWithAccounts[]>([]);
    const [account, setAccount] = useState<BankAccount>({
        accountId: '',
        accountNumber: '',
        openDate: dayjs().format(),
        closeDate: undefined,
        bankId: '',
    });
    const {accounts} = useGlobalState();

    useEffect(() => {
        let sub = WebsocketClient.subscribe(
            {type: "getBanksAndAccountsOverview"},
            readResponse => setBanksAndAccounts(readResponse.banksAndAccountsOverview!)
        );
        return () => WebsocketClient.unsubscribe(sub);
    }, [setBanksAndAccounts]);

    useEffect(() => {
        if (accountId) {
            const sub = WebsocketClient.subscribe(
                {
                    type: "getBankAccount",
                    accountId
                },
                readResponse => setAccount(readResponse.bankAccount!),
            );
            return () => WebsocketClient.unsubscribe(sub);
        }
    }, [accountId]);

    const navigate = useNavigate();

    const saveAndExit = () => {
        WebsocketClient.rpc({
            type: "createOrUpdateBankAccount",
            bankAccount: account
        }).then(() => navigate(-1))
    }

    if (!accounts.hasData()) return null;

    return (<Container maxWidth="xs">
        <Header title={'Bank account'}/>

        <AccountSearchBox
            onSelectedAccountId={accountId => setAccount(prevState => ({
                ...prevState,
                accountId: accountId!
            }))}
            value={account.accountId}
            includeStartsWithPath={[[accounts.getStandardAccountName('Asset'), accounts.getStandardAccountName('BankAccountAsset')]]}
            exclude={banksAndAccounts.flatMap(b => b.accounts).filter(a => a.accountId !== account.accountId).map(a => a.accountId)}
        />

        <FormControl fullWidth>
            <InputLabel id="demo-simple-select-label">Bank</InputLabel>
            <Select
                labelId="demo-simple-select-label"
                id="demo-simple-select"
                value={account.bankId}
                label="Bank"
                onChange={(event, child) => {
                    setAccount(prevState => ({
                        ...prevState,
                        bankId: event.target.value
                    }))
                }}
            >
                {banksAndAccounts.map(b => (
                    <MenuItem key={b.id} value={b.id}>{b.name}</MenuItem>
                ))}
            </Select>
        </FormControl>

        <FormControl fullWidth>
            <DatePicker
                onChange={value => setAccount(prevState => ({
                    ...prevState!,
                    openDate: formatIso(value!)
                }))}
                value={dayjs(account.openDate)}
                label={"Opening date"}
            />
        </FormControl>

        <FormControl fullWidth>
            <DatePicker
                onChange={value => setAccount(prevState => ({
                    ...prevState!,
                    closeDate: value ? formatIso(value!) : undefined
                }))}
                value={account.closeDate ? dayjs(account.closeDate) : undefined}
                label={"Closing date"}
            />
        </FormControl>

        <FormControl fullWidth>
            <TextField
                label={'Account number'}
                value={account.accountNumber || ''}
                onChange={event => setAccount(prevState => ({
                    ...prevState,
                    accountNumber: event.target.value
                }))}
            />
        </FormControl>

        <Button variant={"contained"} onClick={saveAndExit}>{accountId ? 'Save' : 'Add'}</Button>

    </Container>)
}