import {FormControl, TextField} from "@mui/material";
import {useState} from "react";
import {SxProps} from "@mui/system";
import {Theme} from "@mui/material/styles";

const isValidAmount = (s: string | undefined) => /^-?\d+([.,]\d{1,2})?$/g.test(s ?? '')

export type AmountTextFieldProps = {
    initialValue: number;
    setValue: (newValue: number) => void;
    sx?: SxProps<Theme>;
    label?: string
}
export const AmountTextField = ({
                                    initialValue,
                                    setValue,
                                    sx,
                                    label = "Amount"
                                }: AmountTextFieldProps) => {
    const [text, setText] = useState((initialValue / 100).toString());

    const onUpdate = (s: string | undefined) => {
        let inputString = s ?? '';

        setText(inputString);

        const isValid = isValidAmount(inputString);

        if (isValid) {
            const n = s!.replace(",", ".")
            const inCents = Math.round(parseFloat(n) * 100);
            setValue(inCents);
        }
    }

    const isValid = isValidAmount(text);

    return (<FormControl sx={sx}>
        <TextField
            label={label}
            error={!isValid}
            value={text}
            onChange={event => onUpdate(event.target.value)}
            helperText={isValid ? "" : "Invalid amount"}
        >
        </TextField>
    </FormControl>)
}