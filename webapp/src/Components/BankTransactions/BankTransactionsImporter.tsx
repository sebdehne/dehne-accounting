import React, {useState} from "react";
import WebsocketClient from "../../Websocket/websocketClient";
import Header from "../Header";
import {Button, Checkbox, Container, FormControlLabel, styled} from "@mui/material";
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import {arrayBufferToBase64} from "../../utils/formatting";
import {ImportBankTransactionsResult} from "../../Websocket/types/Rpc";
import {useGlobalState} from "../../utils/userstate";
import {useParams} from "react-router-dom";

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
    const {accountId} = useParams();
    const {accountsAsList, userStateV2} = useGlobalState();

    const account = accountsAsList.find(a => a.id == accountId)

    const [selectedFile, setSelectedFile] = useState<File>();
    const [importResult, setImportResult] = useState<ImportBankTransactionsResult>();
    const [importError, setImportError] = useState<string | undefined>(undefined);
    const [ignoreDescriptionDuringImport, setIgnoreDescriptionDuringImport] = useState(false);

    const fileChanged = (e: React.ChangeEvent<HTMLInputElement>) => {
        if ((e?.target?.files?.length ?? 0) > 0) {
            setSelectedFile(e.target.files![0]);
        } else {
            setSelectedFile(undefined);
        }
    }

    const importNow = () => {
        if (selectedFile && account && userStateV2?.selectedRealm) {
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
                        accountId: account.id,
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
        <Container maxWidth="xs" className="App">
            <Header
                title={'Import  for: ' + account?.name ?? "..."}
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