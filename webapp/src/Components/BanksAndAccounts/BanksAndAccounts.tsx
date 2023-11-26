import {Button, ButtonGroup, Container, FormControlLabel, Switch} from "@mui/material";
import Header from "../Header";
import {useCallback, useEffect, useState} from "react";
import WebsocketClient from "../../Websocket/websocketClient";
import {BankAccountView, BankWithAccounts} from "../../Websocket/types/bankaccount";
import "./BanksAndAccounts.css"
import {Amount} from "../Amount";
import {formatLocalDayMonthYear} from "../../utils/formatting";
import {useNavigate} from "react-router-dom";
import IconButton from "@mui/material/IconButton";
import DeleteIcon from "@mui/icons-material/Delete";
import EditIcon from "@mui/icons-material/Edit";
import AddIcon from "@mui/icons-material/Add";
import {useDialogs} from "../../utils/dialogs";
import {AccountDto} from "../../Websocket/types/accounts";
import {useGlobalState} from "../../utils/globalstate";
import dayjs from "dayjs";

export const BanksAndAccounts = () => {
    const [hideClosed, setHideClosed] = useState(true);
    const [editMode, setEditMode] = useState(false);
    const [banksAndAccounts, setBanksAndAccounts] = useState<BankWithAccounts[]>([]);
    const navigate = useNavigate();
    const {accounts} = useGlobalState();

    const {showConfirmationDialog} = useDialogs();

    useEffect(() => {
        let sub = WebsocketClient.subscribe(
            {type: "getBanksAndAccountsOverview"},
            readResponse => setBanksAndAccounts(readResponse.banksAndAccountsOverview!)
        );
        return () => WebsocketClient.unsubscribe(sub);
    }, [setBanksAndAccounts]);

    const flatAccountList = banksAndAccounts
        .flatMap(bank => {
            const map: [BankWithAccounts, BankAccountView][] = bank.accounts.map(ba => ([bank, ba]));
            return map;
        });

    const deleteBankAccount = useCallback((account: AccountDto) => {
        showConfirmationDialog({
            header: "Delete " + account.name + "?",
            content: "Are you sure? This canot be undone",
            confirmButtonText: "Delete",
            onConfirmed: () => {
                WebsocketClient.rpc({
                    type: 'deleteBankAccount',
                    accountId: account.id
                })
            }
        })
    }, [showConfirmationDialog]);

    const editAccount = useCallback((accountId: String) => {
        navigate('/bankaccount/' + accountId);
    }, [navigate]);

    const isClosed = (bankAccount: BankAccountView): boolean =>
        bankAccount.closeDate ? dayjs(bankAccount.closeDate).isBefore(dayjs()) : false;

    if (!accounts.hasData()) return null;

    return (<Container maxWidth="xs">
        <Header title={'Bank accounts'}/>

        <div style={{display: "flex", flexDirection: "row", justifyContent: "space-between"}}>
            {editMode && <Button><IconButton onClick={() => navigate('/bankaccount')}><AddIcon/></IconButton></Button>}
            {!editMode && <FormControlLabel control={<Switch
                checked={hideClosed}
                onChange={(_, checked) => setHideClosed(checked)}
            />} label="Hide closed" labelPlacement={"end"}/>}
            {!editMode && <div></div>}
            <FormControlLabel control={<Switch
                checked={editMode}
                onChange={(_, checked) => setEditMode(checked)}
            />} label="Edit mode" labelPlacement={"start"}/>
        </div>

        <ul className="BankAccounts">
            {flatAccountList
                .filter(([bank, bAccount]) => editMode || !hideClosed || !isClosed(bAccount))
                .map(([bank, bAccount]) => (<li
                key={bAccount.accountId}
                className={editMode ? 'BankAccountNoPointer' : 'BankAccount'}
                onClick={() => !editMode && navigate('/bankaccount_tx/' + bAccount.accountId)}
            >
                <div className="BankAccountLeft">
                    <div
                        className={isClosed(bAccount) ? 'BankAccountLeft01Closed' : 'BankAccountLeft01'}>{accounts.getById(bAccount.accountId)!.name}</div>
                    <div className="BankAccountLeft02">{bank.name} {bAccount.accountNumber &&
                        <span> / {bAccount.accountNumber}</span>}</div>
                </div>
                {!editMode && <div className="BankAccountRight">
                    {bAccount.totalUnbooked > 0 &&
                        <div className="BankAccountRight01">Unbooked: {bAccount.totalUnbooked}</div>}
                    <div className="BankAccountRight02"><Amount amountInCents={bAccount.balance}/></div>
                    <div
                        className="BankAccountRight03">{formatLocalDayMonthYear(dayjs(bAccount.lastKnownTransactionDate))}</div>

                </div>}
                {editMode && <div className="BankAccountRight">
                    <div className="BankAccountRight02">
                        <ButtonGroup>
                            <IconButton
                                onClick={() => editAccount(bAccount.accountId)}
                            ><EditIcon/></IconButton>
                            <IconButton
                                onClick={() => deleteBankAccount(accounts.getById(bAccount.accountId)!)}><DeleteIcon/>
                            </IconButton>
                        </ButtonGroup>
                    </div>
                </div>}

            </li>))}
        </ul>

        <h2 className="BanksHeader">Banks</h2>
        <ul className="BankAccounts">
            {banksAndAccounts.map(b => (<li className="BankAccountNoPointer">
                <div>{b.name}</div>
                {editMode && <div className="BankAccountRight">
                    <div className="BankAccountRight02">
                        <ButtonGroup>
                            <IconButton><EditIcon/></IconButton>
                            <IconButton disabled={b.accounts.length > 0}><DeleteIcon/></IconButton>
                        </ButtonGroup>
                    </div>
                </div>}
            </li>))}
        </ul>


    </Container>)
}