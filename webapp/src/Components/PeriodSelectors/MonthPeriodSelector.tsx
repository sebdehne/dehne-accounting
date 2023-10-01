import React from "react";
import {formatYearMonth, monthDelta} from "../../utils/formatting";
import IconButton from "@mui/material/IconButton";
import ArrowLeftIcon from "@mui/icons-material/ArrowLeft";
import ArrowRightIcon from "@mui/icons-material/ArrowRight";
import './MonthPeriodSelector.css'
import moment from "moment";


type MonthPeriodSelectorProps = {
    period: moment.Moment[];
    setPeriod: React.Dispatch<React.SetStateAction<moment.Moment[]>>;
}
export const MonthPeriodSelector = ({period, setPeriod}: MonthPeriodSelectorProps) => {

    const updatePeriode = (deltaMonth: number) => {
        setPeriod(prevState => {
            let newStart = monthDelta(prevState[0], deltaMonth);
            return ([
                newStart,
                monthDelta(newStart, 1)
            ]);
        })
    }

    return (
        <div className="MonthPeriodSelector">
            <IconButton size="large" onClick={() => updatePeriode(-1)}><ArrowLeftIcon
                fontSize="inherit"/></IconButton>
            <div>{formatYearMonth(period[0])}</div>
            <IconButton size="large" onClick={() => updatePeriode(1)}><ArrowRightIcon
                fontSize="inherit"/></IconButton>
        </div>
    )
}