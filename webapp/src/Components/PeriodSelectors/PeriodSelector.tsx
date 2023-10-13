import React, {useCallback, useEffect, useMemo, useState} from "react";
import {formatIso, formatMonth, formatYear, monthDelta, startOfCurrentMonth, yearDelta} from "../../utils/formatting";
import IconButton from "@mui/material/IconButton";
import ArrowLeftIcon from "@mui/icons-material/ArrowLeft";
import ArrowRightIcon from "@mui/icons-material/ArrowRight";
import ArrowUpwardIcon from "@mui/icons-material/ArrowUpward";
import ArrowDownwardIcon from "@mui/icons-material/ArrowDownward";
import './PeriodSelector.css'
import moment from "moment";
import {PeriodWindow, useGlobalState} from "../../utils/userstate";


export type PeriodSelectorProps = {
    periodLocationInUserState: string[];
}
export const PeriodSelector = ({periodLocationInUserState}: PeriodSelectorProps) => {
    const {userState, setUserState} = useGlobalState();

    const period = useMemo(() => {
        if (userState) {
            let currentObj = userState as any;
            periodLocationInUserState.forEach(loc => {
                currentObj = currentObj[loc] as any
            });
            return currentObj as PeriodWindow;
        }
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
    }, [periodLocationInUserState, setUserState]);


    return (
        <>
            {period?.type === "month" && <MonthPeriodSelector
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
    const [editYear, setEditYear] = useState(false);

    const updatePeriodeMonth = (deltaMonth: number) => {
        const newStart = monthDelta(period[0], deltaMonth);
        const newEnd = monthDelta(newStart, 1);
        setPeriod([newStart, newEnd]);
    }
    const updatePeriodeYear = (deltaMonth: number) => {
        const newStart = yearDelta(period[0], deltaMonth);
        const newEnd = monthDelta(newStart, 1);
        setPeriod([newStart, newEnd]);
    }


    return (
        <div className="MonthPeriodSelector">
            <IconButton size="large" onClick={() => updatePeriodeMonth(-1)}><ArrowLeftIcon
                fontSize="inherit"/></IconButton>
            <div className="MonthPeriodSelectorMonthAndYear">
                <div>{formatMonth(period[0])}</div>
                <div>,&nbsp;</div>
                {editYear && <div className="MonthPeriodSelectorEditYear">
                    <IconButton onClick={() => updatePeriodeYear(1)}><ArrowUpwardIcon/></IconButton>
                    <div onClick={() => setEditYear(false)}>{formatYear(period[0])}</div>
                    <IconButton onClick={() => updatePeriodeYear(-1)}><ArrowDownwardIcon/></IconButton>
                </div>}
                {!editYear && <div onClick={() => setEditYear(true)}>{formatYear(period[0])}</div>}
            </div>
            <IconButton size="large" onClick={() => updatePeriodeMonth(1)}><ArrowRightIcon
                fontSize="inherit"/></IconButton>
        </div>
    )
}

export const PeriodSelectorV2 = () => {
    const {userStateV2, setUserStateV2} = useGlobalState();

    useEffect(() => {
        if (!userStateV2?.rangeFilter || !userStateV2?.periodType) {
            setUserStateV2(prev => ({
                ...prev,
                rangeFilter: {
                    from: formatIso(startOfCurrentMonth()),
                    toExclusive: formatIso(monthDelta(startOfCurrentMonth(), 1)),
                },
                periodType: "month"
            }))
        }
    }, [userStateV2]);

    return (
        <>
            {userStateV2?.rangeFilter && userStateV2.periodType === "month" && <MonthPeriodSelector
                period={[moment(userStateV2.rangeFilter.from), moment(userStateV2.rangeFilter.toExclusive)]}
                setPeriod={p => setUserStateV2(prev => ({
                    ...prev,
                    rangeFilter: {
                        from: formatIso(p[0]),
                        toExclusive: formatIso(p[1]),
                    },
                    periodType: 'month'
                }))}
            />}
        </>
    );

}