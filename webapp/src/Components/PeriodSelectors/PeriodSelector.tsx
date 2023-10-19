import React, {useEffect, useState} from "react";
import {formatIso, formatMonth, formatYear, monthDelta, startOfCurrentMonth, yearDelta} from "../../utils/formatting";
import IconButton from "@mui/material/IconButton";
import ArrowLeftIcon from "@mui/icons-material/ArrowLeft";
import ArrowRightIcon from "@mui/icons-material/ArrowRight";
import ArrowUpwardIcon from "@mui/icons-material/ArrowUpward";
import ArrowDownwardIcon from "@mui/icons-material/ArrowDownward";
import './PeriodSelector.css'
import moment from "moment";
import {useGlobalState} from "../../utils/userstate";


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
    }, [setUserStateV2, userStateV2?.periodType, userStateV2?.rangeFilter]);

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