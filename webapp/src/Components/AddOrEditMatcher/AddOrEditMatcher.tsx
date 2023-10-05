import {useGlobalState} from "../../utils/userstate";
import React, {useEffect, useState} from "react";
import {TransactionMatcher} from "../../Websocket/types/transactionMatcher";
import WebsocketClient from "../../Websocket/websocketClient";
import {v4 as uuidv4} from "uuid";
import {formatIso} from "../../utils/formatting";
import moment from "moment/moment";
import {Box, Button, Container, Paper, Step, StepContent, StepLabel, Stepper, Typography} from "@mui/material";
import {FilterEditor} from "./FilterEditor";
import {ActionEditor} from "./ActionEditor";
import {NameEditor} from "./NameEditor";
import {useNavigate, useParams} from "react-router-dom";
import Header from "../Header";


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

export const AddOrEditMatcher = () => {
    const {userState} = useGlobalState();
    const {matcherId} = useParams();

    const [origMatcher, setOrigMatcher] = useState<TransactionMatcher | undefined>();
    const [matcher, setMatcher] = useState<TransactionMatcher | undefined>();

    const [activeStep, setActiveStep] = React.useState(0);

    const navigate = useNavigate();

    useEffect(() => {
        if (userState && !userState.ledgerId) {
            navigate("/ledger");
        }
    }, [userState, navigate]);

    // fetch matcher to edit
    useEffect(() => {
        if (userState?.ledgerId && !origMatcher && matcherId) {
            const subId = WebsocketClient.subscribe(
                {
                    type: "getMatchers", getMatchersRequest: {
                        ledgerId: userState.ledgerId,
                    }
                },
                notify => {
                    let matchersResponse = notify.readResponse.getMatchersResponse!;
                    if (!origMatcher) {
                        const find = matchersResponse.machers.find(m => m.id === matcherId);
                        if (find) {
                            setOrigMatcher(find);
                            setMatcher(JSON.parse(JSON.stringify(find)) as TransactionMatcher)
                        }
                    }
                }
            )

            return () => WebsocketClient.unsubscribe(subId);
        }
    }, [origMatcher, matcherId, userState, setOrigMatcher, setMatcher]);

    // fetch bank-transaction and create default values for new matcher
    useEffect(() => {
        if (userState?.ledgerId && userState.bankAccountId && userState.transactionId && !matcherId && !matcher) {
            const subId = WebsocketClient.subscribe(
                {
                    type: "getBankTransaction",
                    bankTransactionRequest: {
                        transactionId: userState.transactionId,
                        ledgerId: userState.ledgerId,
                        bankAccountId: userState.bankAccountId,
                    }
                },
                notify => {
                    if (!matcher) {
                        const bankTransaction = notify.readResponse.bankTransaction!;
                        setMatcher((
                            {
                                name: bankTransaction.description ?? '',
                                ledgerId: userState.ledgerId!,
                                id: uuidv4(),
                                filters: [
                                    {
                                        type: "exact",
                                        pattern: bankTransaction.description
                                    }
                                ],
                                action: {
                                    type: "paymentOrIncome",
                                    paymentOrIncomeConfig: {
                                        mainSide: {
                                            categoryToFixedAmountMapping: {},
                                            categoryIdRemaining: "",
                                        },
                                        negatedSide: {
                                            categoryToFixedAmountMapping: {},
                                            categoryIdRemaining: "",
                                        }
                                    }
                                },
                                lastUsed: formatIso(moment()),
                            }
                        ))
                    }
                }
            )

            return () => WebsocketClient.unsubscribe(subId);
        }
    }, [userState, matcher, setMatcher, matcherId]);

    const handleNext = () => setActiveStep((prevActiveStep) => prevActiveStep + 1);
    const handleBack = () => setActiveStep((prevActiveStep) => prevActiveStep - 1);
    const handleReset = () => setActiveStep(0);

    const submit = () => {
        WebsocketClient.rpc({
            type: "addOrReplaceMatcher",
            addOrReplaceMatcherRequest: matcher
        })
            .then(() => navigate(-1));
    };

    return (
        <Container maxWidth="sm" className="App">
            <Header
                title={(origMatcher ? "Edit " : "Add ") + matcher?.name}
            />

            {matcher && <div>

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
                                    <FilterEditor matcher={matcher}
                                                  setMatcher={setMatcher as React.Dispatch<React.SetStateAction<TransactionMatcher>>}
                                                  description={matcher?.name ?? ''}/>}
                                {index === 1 &&
                                    <ActionEditor matcher={matcher}
                                                  setMatcher={setMatcher as React.Dispatch<React.SetStateAction<TransactionMatcher>>}/>}
                                {index === 2 &&
                                    <NameEditor matcher={matcher}
                                                editMode={!!matcher}
                                                setMatcher={setMatcher as React.Dispatch<React.SetStateAction<TransactionMatcher>>}/>}
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
            </div>}

        </Container>

    );
}