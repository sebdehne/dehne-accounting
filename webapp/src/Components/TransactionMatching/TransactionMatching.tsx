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
import {useParams} from "react-router-dom";
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

export const TransactionMatching = () => {
    const {ledgerId, bankAccountId, transactionId} = useParams();
    const [candidates, setCandidates] = useState<TransactionMatcher[]>([]);
    const [showCandidates, setShowCandidates] = useState(false);
    const [transaction, setTransaction] = useState<BankAccountTransactionView>();


    useEffect(() => {
        if (ledgerId && bankAccountId && transactionId) {
            WebsocketClient.rpc({
                type: "getMatchCandidates",
                getMatchCandidatesRequest: {
                    transactionId: parseInt(transactionId),
                    ledgerId,
                    bankAccountId,
                }
            }).then(resp => {
                setCandidates(resp.getMatchCandidatesResult!);
                setShowCandidates(resp.getMatchCandidatesResult!.length > 0);
            });

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


    return (
        <Container maxWidth="sm" className="App">
            <Header title="Matching"/>

            {transaction && <div>
                <div>{formatLocatDayMonth(moment(transaction.datetime))}</div>
                <div><Amount amountInCents={transaction.amount}/></div>
                <div>{transaction.description}</div>
            </div>}

            <div style={{display: "flex", flexDirection: "row", justifyContent: 'space-around'}}>
                <ButtonGroup variant="contained" aria-label="outlined primary button group">
                    <Button variant={!showCandidates ? 'outlined' : 'contained'}
                            onClick={() => setShowCandidates(true)}>Match candidates ({candidates?.length})</Button>
                    <Button variant={showCandidates ? 'outlined' : 'contained'}
                            onClick={() => setShowCandidates(false)}>Add matcher</Button>
                </ButtonGroup>
            </div>


            {transaction && ledgerId && <div>
                {showCandidates && <CandidatesComponent candidates={candidates}/>}
                {!showCandidates && <AddMatchComponent bankTransaction={transaction} ledgerId={ledgerId}/>}
            </div>}

        </Container>
    );
}


const steps = [
    {
        label: 'Select filter',
        description: `For each ad campaign that you create, you can control how much
              you're willing to spend on clicks and conversions, which networks
              and geographical locations you want your ads to show on, and more.`,
    },
    {
        label: 'Create action',
        description:
            'An ad group contains one or more ads which target a shared set of keywords.',
    },
    {
        label: 'Set name',
        description: `Try out different ad text to see what brings in the most customers,
              and learn how to enhance your ads using features like ad extensions.
              If you run into any problems with your ads, find out how to tell if
              they're running and how to resolve approval issues.`,
    },
];

type AddMatchComponentProps = {
    bankTransaction: BankAccountTransactionView;
    ledgerId: string;
}
const AddMatchComponent = ({bankTransaction, ledgerId}: AddMatchComponentProps) => {
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

    // TODO name editor

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
                            {index > 1 && <Typography>{step.description}</Typography>}
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
                    <Button variant="contained" onClick={handleNext} sx={{mt: 1, mr: 1}}>Submit</Button>
                    <Button onClick={handleReset} sx={{mt: 1, mr: 1}}> Reset </Button>
                </Paper>
            )}
        </div>
    )
}


type CandidatesComponentProps = {
    candidates: TransactionMatcher[]
}
const CandidatesComponent = ({candidates}: CandidatesComponentProps) => {

    return (
        <ul className="Candidates">
            {candidates.map(c => (<li key={c.id}>
                <div className="Candidate">
                    <div>{c.name}</div>
                    <div><Button>Book now</Button></div>
                </div>
            </li>))}
        </ul>
    );
}