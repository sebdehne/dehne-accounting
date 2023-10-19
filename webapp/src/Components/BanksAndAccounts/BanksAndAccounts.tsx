import {Container} from "@mui/material";
import Header from "../Header";
import {useEffect, useState} from "react";
import WebsocketClient from "../../Websocket/websocketClient";
import {BankAccountViewV2, BankWithAccounts} from "../../Websocket/types/bankaccount";
import "./BanksAndAccounts.css"
import {Amount} from "../Amount";
import {formatLocalDate} from "../../utils/formatting";
import moment from "moment";
import {useNavigate} from "react-router-dom";

export const BanksAndAccounts = () => {
    const [banksAndAccounts, setBanksAndAccounts] = useState<BankWithAccounts[]>([]);
    const navigate = useNavigate();

    useEffect(() => {
        let sub = WebsocketClient.subscribe(
            {type: "getBanksAndAccountsOverview"},
            notify => setBanksAndAccounts(notify.readResponse.banksAndAccountsOverview!)
        );
        return () => WebsocketClient.unsubscribe(sub);
    }, [setBanksAndAccounts]);

    const flatAccountList = banksAndAccounts
        .flatMap(bank => {
            const map: [BankWithAccounts, BankAccountViewV2][] = bank.accounts.map(ba => ([bank, ba]));
            return map;
        });

    return (<Container maxWidth="xs">
        <Header title={'Bank accounts'}/>

        <ul  className="BankAccounts">
            {flatAccountList.map(([bank, bAccount]) => (<li
                key={bAccount.account.id}
                className="BankAccount"
                onClick={() => navigate('/bankaccount/' + bAccount.account.id)}
            >
                <div className="BankAccountLeft">
                    <div className="BankAccountLeft01">{bAccount.account.name}</div>
                    <div className="BankAccountLeft02">{bank.name} {bAccount.accountNumber && <span> / {bAccount.accountNumber}</span>}</div>
                </div>
                <div className="BankAccountRight">
                    {bAccount.totalUnbooked > 0 && <div className="BankAccountRight01">Unbooked: {bAccount.totalUnbooked}</div>}
                    <div className="BankAccountRight02"><Amount amountInCents={bAccount.balance}/></div>
                    <div className="BankAccountRight03">{formatLocalDate(moment(bAccount.lastKnownTransactionDate))}</div>

                </div>
            </li>))}
        </ul>


    </Container>)
}