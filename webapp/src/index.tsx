import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import App from './Components/App/App';
import reportWebVitals from './reportWebVitals';
import '@fontsource/roboto/300.css';
import '@fontsource/roboto/400.css';
import '@fontsource/roboto/500.css';
import '@fontsource/roboto/700.css';
import theme from "./theme";
import {CssBaseline, ThemeProvider} from "@mui/material";
import {BrowserRouter, Route, Routes} from "react-router-dom";
import {LedgerMain} from "./Components/LedgerMain/LedgerMain";
import {BankTransactions} from "./Components/BankTransactions/BankTransactions";
import {BankTransactionsImporter} from "./Components/BankTransactions/BankTransactionsImporter";
import {BookTransaction} from "./Components/TransactionMatching/BookTransaction";
import {UserStateProvider} from "./utils/userstate";
import {AddOrEditMatcher} from "./Components/AddOrEditMatcher/AddOrEditMatcher";
import {Bookings} from "./Components/Bookings/Bookings";


const root = ReactDOM.createRoot(
    document.getElementById('root') as HTMLElement
);
root.render(
    <React.StrictMode>
        <UserStateProvider>
            <ThemeProvider theme={theme}>
                <CssBaseline/>
                <BrowserRouter basename={process.env.REACT_APP_BASENAME}>
                    <Routes>
                        <Route path="/" element={<App/>}/>
                        <Route path="/ledger" element={<LedgerMain/>}/>
                        <Route path="/bookings" element={<Bookings/>}/>
                        <Route path="/bankaccount" element={<BankTransactions/>}/>
                        <Route path="/bankaccount/import"
                               element={<BankTransactionsImporter/>}/>
                        <Route path="/book/transaction" element={<BookTransaction/>}/>
                        <Route path="/matcher" element={<AddOrEditMatcher/>}/>
                    </Routes>
                </BrowserRouter>
            </ThemeProvider>
        </UserStateProvider>
    </React.StrictMode>
);

// If you want to start measuring performance in your app, pass a function
// to log results (for example: reportWebVitals(console.log))
// or send to an analytics endpoint. Learn more: https://bit.ly/CRA-vitals
reportWebVitals();
