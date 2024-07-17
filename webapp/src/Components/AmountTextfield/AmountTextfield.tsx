import {FormControl, TextField} from "@mui/material";
import {useEffect, useState} from "react";
import {SxProps} from "@mui/system";
import {Theme} from "@mui/material/styles";

const isValidAmount = (s: string | undefined) => /^-?\d+([.,]\d{1,2})?$/g.test(s ?? '')

export type AmountTextFieldProps = {
    initialValue: number | undefined;
    setValue: (newValue: number | undefined) => void;
    sx?: SxProps<Theme>;
    label?: string
    fullWidth?: boolean;
    allowEmpty?: boolean;
}
export const AmountTextField = ({
                                    initialValue,
                                    setValue,
                                    sx,
                                    label = "Amount",
                                    fullWidth = true,
                                    allowEmpty = false
                                }: AmountTextFieldProps) => {
    const [text, setText] = useState<string | undefined>('');

    useEffect(() => {
        if (typeof (initialValue) === "number") {
            setText((initialValue / 100).toString())
        } else {
            setText(undefined);
        }
    }, [initialValue]);

    const onUpdate = (s: string | undefined) => {
        let inputString = s ?? '';

        setText(inputString);

        if (!inputString) {
            if (allowEmpty) {
                setValue(undefined);
            }
        } else {
            const isValid = isValidAmount(inputString);

            if (isValid) {
                const n = s!.replace(",", ".")
                const inCents = Math.round(parseFloat(n) * 100);
                setValue(inCents);
            }
        }
    }

    const isValid = !!text ? isValidAmount(text) : allowEmpty;

    return (<FormControl sx={sx} fullWidth={fullWidth}>
        <TextField
            label={label}
            error={!isValid}
            value={text}
            onChange={event => onUpdate(event.target.value)}
            helperText={isValid ? "" : "Invalid amount"}
        />
    </FormControl>)
}