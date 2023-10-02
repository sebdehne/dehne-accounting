import {
    Box,
    Button,
    ButtonGroup,
    Container,
    Paper,
    Step,
    StepContent,
    StepLabel,
    Stepper,
    Typography
} from "@mui/material";
import Header from "../Header";
import React, {useEffect, useState} from "react";
import {useNavigate, useParams} from "react-router-dom";
import {TransactionMatcher} from "../../Websocket/types/transactionMatcher";
import WebsocketClient from "../../Websocket/websocketClient";
import './TransactionMatching.css'
import {BankAccountTransactionView} from "../../Websocket/types/banktransactions";
import {Amount} from "../Amount";
import {formatLocatDayMonth} from "../../utils/formatting";
import moment from "moment";
import {v4 as uuidv4} from 'uuid';
import {FilterEditor} from "./FilterEditor";
import {ActionEditor} from "./ActionEditor";
import {NameEditor} from "./NameEditor";

export const TransactionMatching = () => {
    const {ledgerId, bankAccountId, transactionId} = useParams();
    const [showCandidates, setShowCandidates] = useState(true);
    const [transaction, setTransaction] = useState<BankAccountTransactionView>();
    const [candidatesCount, setCandidatesCount] = useState(0);

    useEffect(() => {
        if (ledgerId && bankAccountId && transactionId) {
            const subId = WebsocketClient.subscribe(
                {
                    type: "getBankTransaction",
                    bankTransactionRequest: {
                        transactionId: parseInt(transactionId),
                        ledgerId,
                        bankAccountId,
                    }
                },
                notify => setTransaction(notify.readResponse.bankTransaction)
            )

            return () => WebsocketClient.unsubscribe(subId);
        }
    }, [ledgerId, bankAccountId, transactionId, setShowCandidates]);

    const navigate = useNavigate();


    return (
        <Container maxWidth="sm" className="App">
            <Header
                title="Matching"
                backUrl={'/ledger/' + ledgerId + '/bankaccount/' + bankAccountId}
                backName={"Back"}
            />

            {transaction && <div>
                <div>{formatLocatDayMonth(moment(transaction.datetime))}</div>
                <div><Amount amountInCents={transaction.amount}/></div>
                <div>{transaction.description}</div>
            </div>}

            <div style={{display: "flex", flexDirection: "row", justifyContent: 'space-around'}}>
                <ButtonGroup variant="contained" aria-label="outlined primary button group">
                    <Button variant={!showCandidates ? 'outlined' : 'contained'}
                            onClick={() => setShowCandidates(true)}>Match candidates ({candidatesCount})</Button>
                    <Button variant={showCandidates ? 'outlined' : 'contained'}
                            onClick={() => setShowCandidates(false)}>Add matcher</Button>
                </ButtonGroup>
            </div>


            {transaction && ledgerId && bankAccountId && <div>
                {showCandidates && <CandidatesComponent
                    setCandidatesCount={c => {
                        setCandidatesCount(c);
                        setShowCandidates(c > 0);
                    }}
                    transactionId={transaction.id}
                    ledgerId={ledgerId}
                    bankAccountId={bankAccountId}
                    onDone={() => navigate('/ledger/' + ledgerId + '/bankaccount/' + bankAccountId)}
                />}
                {!showCandidates && <AddMatchComponent bankTransaction={transaction} ledgerId={ledgerId}
                                                       onDone={() => setShowCandidates(true)}/>}
            </div>}

        </Container>
    );
}


const steps = [
    {
        label: 'Select filter'
    },
    {
        label: 'Create action',
    },
    {
        label: 'Set name',
    },
];

type AddMatchComponentProps = {
    bankTransaction: BankAccountTransactionView;
    ledgerId: string;
    onDone: () => void;
}
const AddMatchComponent = ({bankTransaction, ledgerId, onDone}: AddMatchComponentProps) => {
    const [activeStep, setActiveStep] = React.useState(0);
    const [matcher, setMatcher] = useState<TransactionMatcher>({
        name: "",
        ledgerId,
        id: uuidv4(),
        filters: [
            {
                type: "exact",
                pattern: bankTransaction.description
            }
        ],
        target: {
            type: "multipleCategoriesBooking",
            multipleCategoriesBooking: {
                creditRules: [],
                debitRules: []
            }
        }
    });

    const handleNext = () => setActiveStep((prevActiveStep) => prevActiveStep + 1);
    const handleBack = () => setActiveStep((prevActiveStep) => prevActiveStep - 1);
    const handleReset = () => setActiveStep(0);

    const submit = () => {
        WebsocketClient.rpc({
            type: "addNewMatcher",
            addNewMatcherRequest: matcher
        }).then(() => onDone())
    };

    return (
        <div>
            <Stepper activeStep={activeStep} orientation="vertical">
                {steps.map((step, index) => (
                    <Step key={step.label}>
                        <StepLabel
                            optional={
                                index === (steps.length - 1) ? (
                                    <Typography variant="caption">Last step</Typography>
                                ) : null
                            }
                        >{step.label}</StepLabel>
                        <StepContent>
                            {index === 0 &&
                                <FilterEditor matcher={matcher} setMatcher={setMatcher} transaction={bankTransaction}/>}
                            {index === 1 &&
                                <ActionEditor matcher={matcher} setMatcher={setMatcher} transaction={bankTransaction}/>}
                            {index === 2 &&
                                <NameEditor matcher={matcher} setMatcher={setMatcher}/>}
                            <Box sx={{mb: 2}}>
                                <div>
                                    <Button variant="contained" onClick={handleNext}
                                            sx={{mt: 1, mr: 1}}>Continue</Button>
                                    {index !== 0 && <Button disabled={index === 0} onClick={handleBack}
                                                            sx={{mt: 1, mr: 1}}>Back</Button>}

                                </div>
                            </Box>
                        </StepContent>
                    </Step>
                ))}
            </Stepper>
            {activeStep === steps.length && (
                <Paper square elevation={0} sx={{p: 3}}>
                    <Button variant="contained" onClick={submit} sx={{mt: 1, mr: 1}}>Submit</Button>
                    <Button onClick={handleBack} sx={{mt: 1, mr: 1}}> Back </Button>
                    <Button onClick={handleReset} sx={{mt: 1, mr: 1}}> Reset </Button>
                </Paper>
            )}
        </div>
    )
}


type CandidatesComponentProps = {
    setCandidatesCount: (c: number) => void;
    ledgerId: string;
    bankAccountId: string;
    transactionId: number;
    onDone: () => void;
}
const CandidatesComponent = ({
                                 setCandidatesCount,
                                 ledgerId,
                                 bankAccountId,
                                 transactionId,
                                 onDone
                             }: CandidatesComponentProps) => {

    const [candidates, setCandidates] = useState<TransactionMatcher[]>([]);

    useEffect(() => {
        WebsocketClient.rpc({
            type: "getMatchCandidates",
            getMatchCandidatesRequest: {
                transactionId: transactionId,
                ledgerId,
                bankAccountId,
            }
        }).then(resp => {
            setCandidates(resp.getMatchCandidatesResult!);
            setCandidatesCount(resp.getMatchCandidatesResult!.length);
        });
    }, [ledgerId, bankAccountId, transactionId, setCandidates, setCandidatesCount]);

    const bookNow = (c: TransactionMatcher) => {
        WebsocketClient.rpc({
            type: "executeMatcher",
            executeMatcherRequest: {
                matcherId: c.id,
                transactionId,
                ledgerId,
                bankAccountId,
            }
        }).then(onDone)
    }

    return (
        <ul className="Candidates">
            {candidates.map(c => (<li key={c.id}>
                <div className="Candidate">
                    <div>{c.name}</div>
                    <div><Button onClick={() => bookNow(c)}>Book now</Button></div>
                </div>
            </li>))}
        </ul>
    );
}