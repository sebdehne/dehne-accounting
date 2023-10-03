import React, {useCallback, useMemo} from "react";
import {formatIso, formatYearMonth, monthDelta} from "../../utils/formatting";
import IconButton from "@mui/material/IconButton";
import ArrowLeftIcon from "@mui/icons-material/ArrowLeft";
import ArrowRightIcon from "@mui/icons-material/ArrowRight";
import './PeriodSelector.css'
import moment from "moment";
import {PeriodWindow, useGlobalState} from "../../utils/userstate";


export type PeriodSelectorProps = {
    periodLocationInUserState: string[];
}
export const PeriodSelector = ({periodLocationInUserState}: PeriodSelectorProps) => {
    const {userState, setUserState} = useGlobalState();

    const period = useMemo(() => {
        let currentObj = userState as any;
        periodLocationInUserState.forEach(loc => {
            currentObj = currentObj[loc] as any
        });
        return currentObj as PeriodWindow;
    }, [periodLocationInUserState, userState]);
    const updatePeriod = useCallback((p: PeriodWindow) => {
        setUserState(prevState => {
            const clone = JSON.parse(JSON.stringify(prevState));
            let currentObj = clone;
            periodLocationInUserState.slice(0, -1).forEach(loc => {
                currentObj = currentObj[loc] as any
            })

            let finalField = periodLocationInUserState[periodLocationInUserState.length - 1];
            currentObj[finalField] = p
            return {...clone};
        })
    }, [periodLocationInUserState]);


    return (
        <>
            {period.type === "month" && <MonthPeriodSelector
                period={[moment(period.startDateTime), moment(period.endDateTime)]}
                setPeriod={p => updatePeriod({
                    startDateTime: formatIso(p[0]),
                    endDateTime: formatIso(p[1]),
                    type: period.type
                })}
            />}
        </>
    );

}

type MonthPeriodSelectorProps = {
    period: moment.Moment[];
    setPeriod: (p: moment.Moment[]) => void;
}
const MonthPeriodSelector = ({period, setPeriod}: MonthPeriodSelectorProps) => {

    const updatePeriode = (deltaMonth: number) => {
        const newStart = monthDelta(period[0], deltaMonth);
        const newEnd = monthDelta(newStart, 1);
        setPeriod([newStart, newEnd]);
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