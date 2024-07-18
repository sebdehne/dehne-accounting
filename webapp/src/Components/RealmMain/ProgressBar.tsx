import {Box, Typography} from "@mui/material";
import LinearProgress, {LinearProgressProps} from "@mui/material/LinearProgress";
import {Amount} from "../Amount";
import {OverridableStringUnion} from "@mui/types";
import {LinearProgressPropsColorOverrides} from "@mui/material/LinearProgress/LinearProgress";

export type LinearProgressWithLabelType = 'warningBelow' | 'errorAbove';

export type LinearProgressWithLabelProps = {
    value: number;
    type: LinearProgressWithLabelType;
    onClick?: () => void;
    budget: number;
}

export const LinearProgressWithLabel = (props: LinearProgressProps & LinearProgressWithLabelProps) => {

    // primary - blue
    // secondary - pink
    // info - light blue
    // inherit - white

    // success - green
    // warning - yellow
    // error - red
    let color: OverridableStringUnion<
        'primary' | 'secondary' | 'error' | 'info' | 'success' | 'warning' | 'inherit',
        LinearProgressPropsColorOverrides
    >;
    if (props.type === "warningBelow") {
        if (props.value < 100) {
            color = 'warning';
        } else {
            color = 'success';
        }
    } else {
        if (props.value <= 100) {
            color = 'success';
        } else {
            color = 'error';
        }
    }

    return (
        <Box sx={{display: 'flex', alignItems: 'center'}} onClick={props.onClick}>
            <Box sx={{width: '100%', mr: 1}}>
                <LinearProgress variant="determinate" {...props} color={color}/>
            </Box>
            <Box sx={{minWidth: '80px', textAlign: 'right'}}>
                <Typography variant="body2" color="text.secondary">
                    <Amount amountInCents={props.budget}/>
                </Typography>
            </Box>
        </Box>
    );
};
