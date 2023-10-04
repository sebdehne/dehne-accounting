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
import {useNavigate} from "react-router-dom";
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
    const {userState, setUserState} = useGlobalState();
    const [matcher, setMatcher] = useState<TransactionMatcher>();
    const [activeStep, setActiveStep] = React.useState(0);
    const [description, setDescription] = useState<string | undefined>('');

    const editMode = !!userState.matcherId;

    useEffect(() => {
        if (userState.ledgerId) {
            if (userState.matcherId) {
                const subId = WebsocketClient.subscribe(
                    {
                        type: "getMatchers", getMatchersRequest: {
                            ledgerId: userState.ledgerId,
                        }
                    },
                    notify => {
                        let matchersResponse = notify.readResponse.getMatchersResponse!;
                        setMatcher(matchersResponse.machers.find(m => m.id === userState.matcherId));
                    }
                )

                return () => WebsocketClient.unsubscribe(subId);
            }

        }
    }, [userState, setMatcher]);

    useEffect(() => {
        if (userState.ledgerId && userState.bankAccountId && userState.transactionId && !userState.matcherId) {
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
                        setDescription(bankTransaction.description);
                        setMatcher((
                            {
                                name: "",
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
    }, [userState, matcher, setDescription, setMatcher]);

    const handleNext = () => setActiveStep((prevActiveStep) => prevActiveStep + 1);
    const handleBack = () => setActiveStep((prevActiveStep) => prevActiveStep - 1);
    const handleReset = () => setActiveStep(0);

    let navigate = useNavigate();

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
                title={(editMode ? "Edit " : "Add ") + matcher?.name}
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
                                                  description={description}/>}
                                {index === 1 &&
                                    <ActionEditor matcher={matcher}
                                                  setMatcher={setMatcher as React.Dispatch<React.SetStateAction<TransactionMatcher>>}/>}
                                {index === 2 &&
                                    <NameEditor matcher={matcher}
                                                editMode={editMode}
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