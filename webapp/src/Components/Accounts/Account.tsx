import {Container} from "@mui/material";
import Header from "../Header";
import {useGlobalState} from "../../utils/userstate";
import {useParams} from "react-router-dom";
import "./Account.css"

export const Account = () => {
    const {accountId} = useParams();
    const {accounts} = useGlobalState();


    if (!accounts.hasData()) return null;

    return (<Container maxWidth="xs" className="App">
        <Header title={"Account"}/>


    </Container>)
}