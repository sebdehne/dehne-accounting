import React, {useCallback, useContext, useState} from "react";
import {Button, Dialog, DialogActions, DialogContent, DialogTitle, TextField} from "@mui/material";
import {UnbookedBankTransactionMatcher} from "../Websocket/types/unbookedTransactions";
import {MatcherView} from "../Components/TransactionMatchingV2/MatcherView";
import {useGlobalState} from "./userstate";
import {AccountSearchBox} from "../Components/AccountSearchBox/AccountSearchBox";


export type ConfirmationDialogProps = {
    confirmButtonText?: string;
    header: string;
    content: React.ReactNode;
    onConfirmed: () => void;
}

export type BookMatcherConfirmationProps = {
    matcher: UnbookedBankTransactionMatcher;
    onConfirmed: (overrideMemo: string | undefined) => void;
    initialMemo?: string;
}

export type MergeAccountDialogProps = {
    sourceAccountId: string;
    onConfirmed: (targetAccountId: string) => void;
}

export type ContextType = {
    showConfirmationDialog: (props: ConfirmationDialogProps) => void;
    showBookMatcherConfirmation: (props: BookMatcherConfirmationProps) => void;
    showMergeAccountDialog: (props: MergeAccountDialogProps) => void;
}

const DialogsProviderContext = React.createContext({} as ContextType);


export type DialogsProviderProps = {
    children?: React.ReactNode;
}
export const DialogsProvider = ({children,}: DialogsProviderProps) => {
    const [confirmationProps, setConfirmationProps] = useState<ConfirmationDialogProps | undefined>(undefined);
    const [bookMatcherConfirmationProps, setBookMatcherConfirmationProps] = useState<BookMatcherConfirmationProps | undefined>(undefined);
    const [mergeAccountDialogProps, setMergeAccountDialogProps] = useState<MergeAccountDialogProps | undefined>();

    const showConfirmationDialog = useCallback((props: ConfirmationDialogProps) => {
        setConfirmationProps(props);
    }, [setConfirmationProps]);
    const showBookMatcherConfirmation = useCallback((props: BookMatcherConfirmationProps) => {
        setBookMatcherConfirmationProps(props);
    }, [setBookMatcherConfirmationProps]);
    const showMergeAccountDialog = useCallback((props: MergeAccountDialogProps) => {
        setMergeAccountDialogProps(props);
    }, [setMergeAccountDialogProps]);

    return (<DialogsProviderContext.Provider value={{
        showConfirmationDialog,
        showBookMatcherConfirmation,
        showMergeAccountDialog
    }}>

        {confirmationProps &&
            <ConfirmationDialog props={confirmationProps} close={() => setConfirmationProps(undefined)}/>}
        {bookMatcherConfirmationProps &&
            <BookMatcherConfirmationDialog props={bookMatcherConfirmationProps}
                                           close={() => setBookMatcherConfirmationProps(undefined)}/>}
        {mergeAccountDialogProps &&
            <MergeAccountDialogC props={mergeAccountDialogProps}
                                 close={() => setMergeAccountDialogProps(undefined)}/>}

        {children}
    </DialogsProviderContext.Provider>)
}

export const useDialogs = () => {
    const context = useContext(DialogsProviderContext);
    if (!context) {
        throw new Error("useDialogs must be used within a DialogsProvider");
    }
    return context;
};


type ConfirmationDialogP = {
    close: () => void;
    props: ConfirmationDialogProps;
}
const ConfirmationDialog = ({close, props}: ConfirmationDialogP) => {

    return (
        <Dialog open={true} onClose={close}>
            <DialogTitle>{props.header}</DialogTitle>
            <DialogContent>
                {props.content}
            </DialogContent>
            <DialogActions>
                <Button variant={"contained"} onClick={close}>Abort</Button>
                <Button onClick={() => {
                    close();
                    props.onConfirmed()
                }} autoFocus>
                    {props.confirmButtonText ?? "OK"}
                </Button>
            </DialogActions>
        </Dialog>
    )
}

type MergeAccountDialogCProps = {
    close: () => void,
    props: MergeAccountDialogProps
}
const MergeAccountDialogC = ({close, props}: MergeAccountDialogCProps) => {
    const {accounts} = useGlobalState();
    const [targetAccountId, setTargetAccountId] = useState<string>();

    if (!accounts.hasData()) return null;

    let sourceAccount = accounts.getByIdExpanded(props.sourceAccountId);

    return (
        <Dialog open={true} fullWidth={true} onClose={close}>
            <DialogTitle>Merge {accounts.generateParentsString(props.sourceAccountId)} - {sourceAccount.account.name}?</DialogTitle>

            <DialogContent>
                <div>Select target account to which all bookings should be moved to:</div>
                <AccountSearchBox
                    onSelectedAccountId={accountId => setTargetAccountId(accountId)}
                    value={targetAccountId}
                    title={"Target account"}
                />
                <div>If the account has no children, then it is automatically removed after the merge.</div>
            </DialogContent>

            <DialogActions>
                <Button variant={"contained"} onClick={close}>Abort</Button>
                <Button disabled={!targetAccountId} onClick={() => {
                    close();
                    if (targetAccountId) {
                        props.onConfirmed(targetAccountId)
                    }
                }} autoFocus> Merge </Button>
            </DialogActions>
        </Dialog>
    )
}

type BookMatcherConfirmationP = {
    close: () => void,
    props: BookMatcherConfirmationProps
}
const BookMatcherConfirmationDialog = ({close, props}: BookMatcherConfirmationP) => {
    const [overrideMemo, setOverrideMemo] = useState<string>(props.initialMemo ?? '');

    return (
        <Dialog open={true} fullWidth={true} onClose={close}>
            <DialogTitle>Execute?</DialogTitle>
            <DialogContent>

                <MatcherView matcher={props.matcher} buttons={undefined}/>

                <TextField
                    value={overrideMemo}
                    onChange={event => setOverrideMemo(event.target.value ?? '')}
                    fullWidth={true}
                    label={'Memo'}/>
            </DialogContent>
            <DialogActions>
                <Button variant={"contained"} onClick={() => {
                    close();
                    props.onConfirmed(undefined)
                }} autoFocus>
                    Book without memo
                </Button>
                <Button variant={"contained"} onClick={() => {
                    close();
                    props.onConfirmed(overrideMemo ? overrideMemo : undefined)
                }} autoFocus>
                    Book with memo
                </Button>
                <Button onClick={close}>Abort</Button>
            </DialogActions>
        </Dialog>
    )
}