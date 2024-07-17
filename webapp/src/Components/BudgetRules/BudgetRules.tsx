import {Container} from "@mui/material";
import Header from "../Header";
import React from "react";
import {useGlobalState} from "../../utils/globalstate";
import {Loading} from "../loading";
import {AccountSearchBox} from "../AccountSearchBox/AccountSearchBox";
import {useNavigate} from "react-router-dom";


export const BudgetRules = () => {
    const {userStateV2, setUserStateV2, realm, userInfo} = useGlobalState();
    let navigate = useNavigate();

    if (!userInfo || !realm) return <Loading/>;

    return (
        <Container maxWidth="xs" className="App">
            <Header
                title="Budget - choose account"
                userIsAdmin={userInfo.admin}
            />

            <div style={{margin: "20px"}}></div>

            <AccountSearchBox onSelectedAccountId={a => {
                if (a) {
                    navigate('/budget/' + a);
                }
            }} value={undefined}/>

        </Container>
    )
}
