import {
    BookingConfigurationForOneSide,
    TransactionMatcher,
    TransactionMatcherActionType
} from "../../Websocket/types/transactionMatcher";
import React, {useCallback, useState} from "react";
import DeleteIcon from "@mui/icons-material/Delete";
import AddIcon from "@mui/icons-material/Add";
import IconButton from "@mui/material/IconButton";
import {FormControl, InputLabel, MenuItem, Select} from "@mui/material";
import "./ActionEditor.css";
import {useGlobalState} from "../../utils/userstate";
import {CategorySearchBox2} from "../CategorySearchBox/CategorySearchBox2";
import {AmountTextField} from "../AmountTextfield/AmountTextfield";
import {amountInCentsToString} from "../../utils/formatting";


export type ActionEditorProps = {
    matcher: TransactionMatcher;
    setMatcher: React.Dispatch<React.SetStateAction<TransactionMatcher>>;
}

export const ActionEditor = ({matcher, setMatcher}: ActionEditorProps) => {
    const setConfig = useCallback((isMain: boolean) => (c: BookingConfigurationForOneSide) => {
        setMatcher(prevState => ({
            ...prevState,
            action: {
                ...prevState.action,
                paymentOrIncomeConfig: {
                    ...prevState.action.paymentOrIncomeConfig,
                    mainSide: isMain ? c : prevState.action.paymentOrIncomeConfig!.mainSide,
                    negatedSide: isMain ? prevState.action.paymentOrIncomeConfig!.negatedSide : c,
                }
            }
        }))
    }, [setMatcher]);

    return (<div>
        <FormControl sx={{m: 1, minWidth: 120}}>
            <InputLabel id="demo-simple-select-helper-label">Target type</InputLabel>
            <Select
                labelId="demo-simple-select-helper-label"
                id="demo-simple-select-helper"
                value={matcher.action.type}
                label="Target type"
                onChange={(event,) => setMatcher(prevState => ({
                    ...prevState,
                    target: {
                        ...prevState.action,
                        type: event.target.value as TransactionMatcherActionType
                    }
                }))}
            >
                <MenuItem value={'paymentOrIncome'}>Payment/income</MenuItem>
                <MenuItem value={'bankTransfer'}>bank transfer</MenuItem>
            </Select>
        </FormControl>

        {matcher.action.type === "paymentOrIncome" && <div>
            <ConfigEditor
                title={'main side'}
                config={matcher.action.paymentOrIncomeConfig!.mainSide}
                setConfig={setConfig(true)}/>
            <ConfigEditor
                title={'negated side'}
                config={matcher.action.paymentOrIncomeConfig!.negatedSide}
                setConfig={setConfig(false)}/>
        </div>}
        {matcher.action.type === "bankTransfer" && <div>
            <CategorySearchBox2
                value={matcher.action.transferCategoryId}
                includeIntermediate={true}
                onSelectedCategoryId={categoryId => setMatcher(prevState => ({
                ...prevState,
                action: {
                    ...prevState.action,
                    transferCategoryId: categoryId!
                }
            }))}/>
        </div>}
    </div>);
}

type ConfigEditorProps = {
    title: string;
    config: BookingConfigurationForOneSide;
    setConfig: (c: BookingConfigurationForOneSide) => void;
}

const ConfigEditor = ({title, config, setConfig}: ConfigEditorProps) => {
    const {categoriesAsList, userState} = useGlobalState();

    const categoryName = useCallback((categoryId: string) =>
            categoriesAsList.find(c => c.id === categoryId)?.name,
        [categoriesAsList]
    );

    const [addFixedMappingCategory, setAddFixedMappingCategory] = useState<string>();
    const [addFixedMappingAmountInCents, setAddFixedMappingAmountInCents] = useState<number>(0);

    const removeFixedAmountMapping = useCallback((categoryId: string) => {
        const {[categoryId]: _, ...rest} = config.categoryToFixedAmountMapping;
        setConfig(({
            ...config,
            categoryToFixedAmountMapping: rest
        }));
    }, [setConfig, config]);
    const addFixedAmountMapping = useCallback(() => {
        const categoryId = addFixedMappingCategory!;
        setConfig(({
            ...config,
            categoryToFixedAmountMapping: {
                ...config.categoryToFixedAmountMapping,
                [categoryId]: addFixedMappingAmountInCents
            }
        }))
    }, [setConfig, addFixedMappingCategory, addFixedMappingAmountInCents, config]);

    return (<div>
        <h4>{title} config</h4>

        <ul className="FixedAmountMapping">
            {userState && Object.entries(config.categoryToFixedAmountMapping).map(([categoryId, amount]) => (
                <li key={categoryId} className="FixedAmountMappingEntry">
                    <div className="FixedAmountMappingEntrySummary">
                        <div>category: {categoryName(categoryId)}</div>
                        <div>Amount: {amountInCentsToString(
                            amount,
                            userState.locale,
                        )}</div>
                    </div>
                    <IconButton size="large" onClick={() => removeFixedAmountMapping(categoryId)}><DeleteIcon
                        fontSize="inherit"/></IconButton>
                </li>))}
        </ul>

        <div>
            <div style={{border: '1px white solid'}}>
                <AmountTextField initialValue={addFixedMappingAmountInCents} setValue={newValue => setAddFixedMappingAmountInCents(newValue)}/>
                <CategorySearchBox2
                    value={undefined}
                    includeIntermediate={true}
                    onSelectedCategoryId={categoryId => setAddFixedMappingCategory(categoryId)}
                    title={"Fixed mapping category"}
                />
                <IconButton size="large" onClick={addFixedAmountMapping}><AddIcon fontSize="inherit"/></IconButton>
            </div>

            <CategorySearchBox2
                includeIntermediate={true}
                value={config.categoryIdRemaining}
                onSelectedCategoryId={categoryId => setConfig(({
                    ...config,
                    categoryIdRemaining: categoryId!
                }))}
                title={"Main category"}
            />
        </div>
    </div>)
}