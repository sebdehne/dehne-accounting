import {useParams} from "react-router-dom";
import React, {useEffect, useState} from "react";
import {BankAccountView} from "../../Websocket/types/bankaccount";
import WebsocketClient from "../../Websocket/websocketClient";
import Header from "../Header";
import {Button, Checkbox, Container, FormControlLabel, styled} from "@mui/material";
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import {arrayBufferToBase64} from "../../utils/formatting";
import {ImportBankTransactionsResult} from "../../Websocket/types/Rpc";

const VisuallyHiddenInput = styled('input')({
    clip: 'rect(0 0 0 0)',
    clipPath: 'inset(50%)',
    height: 1,
    overflow: 'hidden',
    position: 'absolute',
    bottom: 0,
    left: 0,
    whiteSpace: 'nowrap',
    width: 1,
});


export const BankTransactionsImporter = () => {
    const {ledgerId, bankAccountId} = useParams();
    const [bankAccount, setBankAccount] = useState<BankAccountView>()
    const [selectedFile, setSelectedFile] = useState<File>();
    const [importResult, setImportResult] = useState<ImportBankTransactionsResult>();
    const [importError, setImportError] = useState<string |undefined>(undefined);
    const [ignoreDescriptionDuringImport, setIgnoreDescriptionDuringImport] = useState(false);

    useEffect(() => {
        const subId = WebsocketClient
            .subscribe(
                {type: "getBankAccounts", ledgerId},
                notify => setBankAccount(notify.readResponse.bankAccounts?.find(b => b.id === bankAccountId))
            );

        return () => WebsocketClient.unsubscribe(subId);
    }, [bankAccountId, ledgerId]);

    const fileChanged = (e: React.ChangeEvent<HTMLInputElement>) => {
        if (e?.target?.files?.length ?? 0 > 0) {
            setSelectedFile(e.target.files![0]);
        } else {
            setSelectedFile(undefined);
        }
    }
    const importNow = () => {
        if (selectedFile && bankAccountId && ledgerId) {
            const fr = new FileReader();
            fr.onload = () => {
                const data: ArrayBuffer = fr.result as ArrayBuffer;
                const base64 = arrayBufferToBase64(data);

                WebsocketClient.rpc({
                    type: "importBankTransactions",
                    importBankTransactionsRequest: {
                        dataBase64: base64,
                        filename: selectedFile.name,
                        duplicationHandlerType: ignoreDescriptionDuringImport ? "sameDateAndAmount" : "sameDateAmountAndDescription",
                        bankAccountId,
                        ledgerId,
                    }
                }).then(resp => {
                    setImportResult(resp.importBankTransactionsResult);
                    setImportError(resp.error);
                })
            }
            fr.readAsArrayBuffer(selectedFile!);
        }
    }


    return (
        <Container maxWidth="sm" className="App">
            <Header
                title={'Import  for: ' + bankAccount?.name ?? "..."}
                backName={"Back"}
                backUrl={'/ledger/' + ledgerId + '/bankaccount/' + bankAccountId}
            />

            <div>
                <Button component="label" variant="contained" startIcon={<CloudUploadIcon/>}>
                    Upload file
                    <VisuallyHiddenInput type="file" onChange={fileChanged}/>
                </Button>
                <div>{selectedFile?.name}</div>
                <div>{selectedFile?.size} bytes</div>
            </div>

            {selectedFile && <div>
                <FormControlLabel
                    label="Do not compare text for duplicate detection"
                    control={<Checkbox
                        checked={ignoreDescriptionDuringImport}
                        onChange={(_, checked) => setIgnoreDescriptionDuringImport(checked)}
                    />}
                />
                <Button onClick={() => importNow()}>Import</Button>
            </div>}
            {importError && <div>Error: {importError}</div>}
            {!importError && <div>
                <div>Imported: {importResult?.imported}</div>
                <div>Skipped: {importResult?.skipped}</div>
            </div>}


        </Container>
    )
}