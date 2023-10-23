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
import {Button, ButtonGroup} from "@mui/material";
import {PeriodType} from "../../Websocket/types/UserStateV2";

export const PeriodSelectorV2 = () => {
    const {userStateV2, setUserStateV2} = useGlobalState();
    const [showModeEditor, setShowModeEditor] = useState(false);

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

    const changeMode = (mode: PeriodType) => {
        if (mode === "month") {
            setUserStateV2(prev => ({
                ...prev,
                periodType: "month",
                rangeFilter: {
                    from: formatIso(startOfCurrentMonth()),
                    toExclusive: formatIso(monthDelta(startOfCurrentMonth(), 1)),
                },
            }));
        } else if (mode === "all") {
            setUserStateV2(prev => ({
                ...prev,
                periodType: "all",
                rangeFilter: {
                    from: formatIso(moment("1970-01-01")),
                    toExclusive: formatIso(moment("2999-01-01")),
                },
            }));
        }
        setShowModeEditor(false)
    }

    if (!userStateV2?.periodType ||!userStateV2.rangeFilter) return null

    return (
        <>
            {showModeEditor && <div style={{display: "flex", flexDirection: "row", justifyContent: "space-between"}}>
                <div>Change mode:</div>
                <ButtonGroup>
                    <Button onClick={() => changeMode("month")}>Month</Button>
                    <Button onClick={() => changeMode("all")}>All</Button>
                </ButtonGroup>
            </div>}
            {userStateV2.periodType === "month" && <MonthPeriodSelector
                openModeEditor={() => setShowModeEditor(true)}
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
            {userStateV2.periodType === "all" && <div className="PeriodSelector">
                <h3 onClick={() => setShowModeEditor(true)}>All time</h3>
            </div>}
        </>
    );

}

type MonthPeriodSelectorProps = {
    period: moment.Moment[];
    setPeriod: (p: moment.Moment[]) => void;
    openModeEditor: () => void;
}
const MonthPeriodSelector = ({period, setPeriod, openModeEditor}: MonthPeriodSelectorProps) => {
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
        <div className="PeriodSelector">
            <IconButton size="large" onClick={() => updatePeriodeMonth(-1)}><ArrowLeftIcon
                fontSize="inherit"/></IconButton>
            <div className="MonthPeriodSelectorMonthAndYear">
                <div onClick={openModeEditor}>{formatMonth(period[0])}</div>
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

