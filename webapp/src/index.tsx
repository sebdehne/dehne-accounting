import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import '@fontsource/roboto/300.css';
import '@fontsource/roboto/400.css';
import '@fontsource/roboto/500.css';
import '@fontsource/roboto/700.css';
import {CssBaseline, ThemeProvider} from "@mui/material";
import {BrowserRouter, Route, Routes} from "react-router-dom";
import {BankTransactionsImporter} from "./Components/BankTransactionsImporter";
import {GlobalStateProvider} from "./utils/globalstate";
import {LocalizationProvider} from '@mui/x-date-pickers';
import {AdapterDayjs} from '@mui/x-date-pickers/AdapterDayjs'
import 'dayjs/locale/nb';
import {ChooseRealm} from "./Components/ChooseRealm/ChooseRealm";
import {RealmMain} from "./Components/RealmMain/RealmMain";
import {BanksAndAccounts} from "./Components/BanksAndAccounts/BanksAndAccounts";
import {DialogsProvider} from "./utils/dialogs";
import {TransactionMatchingV2} from "./Components/TransactionMatchingV2/TransactionMatchingV2";
import {AddOrEditMatcherV2} from "./Components/TransactionMatchingV2/AddOrEditMatcherV2";
import {BookingForAccountViewer} from "./Components/BookingForAccountViewer/BookingForAccountViewer";
import {BookingViewerEditor} from "./Components/BookingViewerEditor/BookingViewerEditor";
import {Accounts} from "./Components/Accounts/Accounts";
import {Account} from "./Components/Accounts/Account";
import {AddOrReplaceBankAccount} from "./Components/BanksAndAccounts/AddOrReplaceBankAccount";
import {UserManagement} from "./Components/Users/UserManagement";
import {Backups} from "./Components/Backups/Backups";
import {theme} from "./theme";
import {BudgetRules} from "./Components/BudgetRules/BudgetRules";
import {BudgetRulesForAccount} from "./Components/BudgetRules/BudgetRulesForAccount";

const root = ReactDOM.createRoot(
    document.getElementById('root') as HTMLElement
);

root.render(
    <React.StrictMode>
        <GlobalStateProvider>
            <ThemeProvider theme={theme}>
                <DialogsProvider>
                    <LocalizationProvider dateAdapter={AdapterDayjs} adapterLocale="nb">
                        <CssBaseline/>
                        <BrowserRouter basename={import.meta.env.BASE_URL}>
                            <Routes>
                                <Route path="/" element={<RealmMain/>}/>

                                <Route path="/realm" element={<ChooseRealm/>}/>

                                <Route path="/bankaccounts" element={<BanksAndAccounts/>}/>
                                <Route path="/bankaccount_tx/:accountId/import" element={<BankTransactionsImporter/>}/>

                                <Route path="/bankaccount" element={<AddOrReplaceBankAccount/>}/>
                                <Route path="/bankaccount/:accountId" element={<AddOrReplaceBankAccount/>}/>

                                <Route path="/matchers" element={<TransactionMatchingV2/>}/>
                                <Route path="/matchers/:accountId/:txId" element={<TransactionMatchingV2/>}/>
                                <Route path="/matcher/:matcherId" element={<AddOrEditMatcherV2/>}/>
                                <Route path="/matcher" element={<AddOrEditMatcherV2/>}/>
                                <Route path="/matcher/:accountId/:txId" element={<AddOrEditMatcherV2/>}/>

                                <Route path="/bookings/:accountId" element={<BookingForAccountViewer/>}/>
                                <Route path="/booking/:bookingId" element={<BookingViewerEditor/>}/>
                                <Route path="/booking" element={<BookingViewerEditor/>}/>

                                <Route path="/accounts" element={<Accounts/>}/>
                                <Route path="/account" element={<Account/>}/>
                                <Route path="/account/:accountId" element={<Account/>}/>

                                <Route path="/users" element={<UserManagement/>}/>
                                <Route path="/backups" element={<Backups/>}/>

                                <Route path="/budget" element={<BudgetRules/>}/>
                                <Route path="/budget/:accountId" element={<BudgetRulesForAccount/>}/>
                            </Routes>
                        </BrowserRouter>
                    </LocalizationProvider>
                </DialogsProvider>
            </ThemeProvider>
        </GlobalStateProvider>
    </React.StrictMode>
);

