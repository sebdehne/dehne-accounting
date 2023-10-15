import React, {useCallback, useContext, useState} from "react";
import {Button, Dialog, DialogActions, DialogContent, DialogTitle} from "@mui/material";


export type ConfirmationDialogProps = {
    confirmButtonText?: string;
    header: string,
    content: React.ReactNode,
    onConfirmed: () => void,
}

export type ContextType = {
    showConfirmationDialog: (props: ConfirmationDialogProps) => void
}

const DialogsProviderContext = React.createContext({} as ContextType);


export type DialogsProviderProps = {
    children?: React.ReactNode;
}
export const DialogsProvider = ({children,}: DialogsProviderProps) => {
    const [confirmationProps, setConfirmationProps] = useState<ConfirmationDialogProps | undefined>(undefined);

    const showConfirmationDialog = useCallback((props: ConfirmationDialogProps) => {
        setConfirmationProps(props);
    }, [setConfirmationProps]);

    return (<DialogsProviderContext.Provider value={{
        showConfirmationDialog
    }}>

        {confirmationProps &&
            <ConfirmationDialog props={confirmationProps} close={() => setConfirmationProps(undefined)}/>}

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
    close: () => void,
    props: ConfirmationDialogProps
}
const ConfirmationDialog = ({close, props}: ConfirmationDialogP) => {

    return (
        <Dialog open={true}>
            <DialogTitle>{props.header}</DialogTitle>
            <DialogContent>
                {props.content}
            </DialogContent>
            <DialogActions>
                <Button onClick={close}>Disagree</Button>
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