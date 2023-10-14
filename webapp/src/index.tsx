import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
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
import {GlobalStateProvider} from "./utils/userstate";
import {AddOrEditMatcher} from "./Components/AddOrEditMatcher/AddOrEditMatcher";
import {Bookings} from "./Components/Bookings/Bookings";
import {ChooseLedger} from "./Components/ChooseLedger/ChooseLedger";
import {Categories} from "./Components/Categories/Categories";
import {AddOrEditCategory} from "./Components/Categories/AddOrEditCategory";
import {AddOrEditBooking} from "./Components/Booking/AddOrEditBooking";
import { LocalizationProvider } from '@mui/x-date-pickers';
import { AdapterMoment } from '@mui/x-date-pickers/AdapterMoment'
import 'moment/locale/nb';
import {ChooseRealm} from "./Components/ChooseRealm/ChooseRealm";
import {RealmMain} from "./Components/RealmMain/RealmMain";
import {BanksAndAccounts} from "./Components/BanksAndAccounts/BanksAndAccounts";
import {BankTransactionsV2} from "./Components/BankTransactionsV2/BankTransactionsV2";

const root = ReactDOM.createRoot(
    document.getElementById('root') as HTMLElement
);
root.render(
    <React.StrictMode>
        <GlobalStateProvider>
            <ThemeProvider theme={theme}>
                <LocalizationProvider dateAdapter={AdapterMoment} adapterLocale="nb">
                    <CssBaseline/>
                    <BrowserRouter basename={process.env.REACT_APP_BASENAME}>
                        <Routes>
                            <Route path="/" element={<RealmMain/>}/>
                            <Route path="/realm" element={<ChooseRealm/>}/>
                            <Route path="/bankaccounts" element={<BanksAndAccounts/>}/>
                            <Route path="/bankaccount/:accountId" element={<BankTransactionsV2/>}/>
                            <Route path="/bankaccount/:accountId/import" element={<BankTransactionsImporter/>}/>

                            <Route path="/categories" element={<Categories/>}/>
                            <Route path="/category" element={<AddOrEditCategory/>}/>
                            <Route path="/category/:editCategoryId" element={<AddOrEditCategory/>}/>

                            <Route path="/bankaccount" element={<BankTransactions/>}/>
                            <Route path="/bankaccount/import"
                                   element={<BankTransactionsImporter/>}/>

                            <Route path="/book/transaction" element={<BookTransaction/>}/>

                            <Route path="/matcher" element={<AddOrEditMatcher/>}/>
                            <Route path="/matcher/:matcherId" element={<AddOrEditMatcher/>}/>

                            <Route path="/bookings" element={<Bookings/>}/>
                            <Route path="/booking" element={<AddOrEditBooking/>}/>
                            <Route path="/booking/:bookingId" element={<AddOrEditBooking/>}/>
                        </Routes>
                    </BrowserRouter>
                </LocalizationProvider>
            </ThemeProvider>
        </GlobalStateProvider>
    </React.StrictMode>
);

// If you want to start measuring performance in your app, pass a function
// to log results (for example: reportWebVitals(console.log))
// or send to an analytics endpoint. Learn more: https://bit.ly/CRA-vitals
reportWebVitals();
