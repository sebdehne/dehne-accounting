import React, {useCallback, useEffect, useMemo, useState} from "react";
import {formatIso, formatMonth, formatYear, monthDelta, startOfCurrentMonth, yearDelta} from "../../utils/formatting";
import IconButton from "@mui/material/IconButton";
import ArrowLeftIcon from "@mui/icons-material/ArrowLeft";
import ArrowRightIcon from "@mui/icons-material/ArrowRight";
import ArrowUpwardIcon from "@mui/icons-material/ArrowUpward";
import ArrowDownwardIcon from "@mui/icons-material/ArrowDownward";
import LockIcon from '@mui/icons-material/Lock';
import LockOpenIcon from '@mui/icons-material/LockOpen';
import './PeriodSelector.css'
import {useGlobalState} from "../../utils/globalstate";
import {Button, ButtonGroup} from "@mui/material";
import {PeriodType} from "../../Websocket/types/UserStateV2";
import dayjs from "dayjs";
import WebsocketClient from "../../Websocket/websocketClient";

export const PeriodSelectorV2 = () => {
    const {userStateV2, setUserStateV2, realm} = useGlobalState();
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
                    from: formatIso(dayjs("1970-01-01")),
                    toExclusive: formatIso(dayjs("2999-01-01")),
                },
            }));
        }
        setShowModeEditor(false)
    }

    const closureState: ClosureState = useMemo(() => {
        if (realm && userStateV2 && userStateV2.rangeFilter && userStateV2.periodType === "month") {
            const periode = dayjs(userStateV2.rangeFilter.from);

            const closure = dayjs(realm.closure);
            const closureNext = closure.add(1, "month");

            if (periode.isBefore(closure)) return 'closed';
            if (periode.isSame(closure)) return 'closedCanOpen';
            if (periode.isAfter(closureNext)) return 'open';

            return 'openCanClose';
        } else {
            return 'unknown';
        }
    }, [realm, userStateV2]);

    if (!userStateV2?.periodType || !userStateV2.rangeFilter || !realm) return null

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
                closureState={closureState}
                openModeEditor={() => setShowModeEditor(true)}
                period={[dayjs(userStateV2.rangeFilter.from), dayjs(userStateV2.rangeFilter.toExclusive)]}
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

type ClosureState = 'closedCanOpen' | 'closed' | 'openCanClose' | 'open' | 'unknown';

const MonthPeriodSelector = ({period, setPeriod, openModeEditor, closureState}: {
    period: dayjs.Dayjs[];
    setPeriod: (p: dayjs.Dayjs[]) => void;
    openModeEditor: () => void;
    closureState: ClosureState;
}) => {
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

    const onClosureButtonClicked = useCallback(() => {
        if (closureState === 'closedCanOpen' || closureState === 'openCanClose') {
            WebsocketClient.rpc({
                type: closureState === 'closedCanOpen' ? "reopenPreviousMonth" : "closeNextMonth"
            })
        }
    }, [closureState]);


    return (
        <div className="PeriodSelector">
            <IconButton size="large" onClick={() => updatePeriodeMonth(-1)}>
                <ArrowLeftIcon fontSize="inherit"/>
            </IconButton>
            <div className="MonthPeriodSelectorMonthAndYear">
                <div onClick={openModeEditor}>{formatMonth(period[0])}</div>
                <div>,&nbsp;</div>
                {editYear && <div className="MonthPeriodSelectorEditYear">
                    <IconButton onClick={() => updatePeriodeYear(1)}><ArrowUpwardIcon/></IconButton>
                    <div onClick={() => setEditYear(false)}>{formatYear(period[0])}</div>
                    <IconButton onClick={() => updatePeriodeYear(-1)}><ArrowDownwardIcon/></IconButton>
                </div>}
                {!editYear && <div onClick={() => setEditYear(true)}>{formatYear(period[0])}</div>}
                <div style={{margin: '0px'}}>
                    {closureState === 'closed' &&
                        <LockIcon style={{marginTop: '8px', marginLeft: '10px', color: 'gray'}}/>}
                    {closureState === 'closedCanOpen' &&
                        <IconButton onClick={onClosureButtonClicked}><LockIcon style={{margin: '8px'}}/></IconButton>}
                    {closureState === 'open' &&
                        <LockOpenIcon style={{marginTop: '8px', marginLeft: '10px', color: 'gray'}}/>}
                    {closureState === 'openCanClose' && <IconButton onClick={onClosureButtonClicked}><LockOpenIcon
                        style={{margin: '8px'}}/></IconButton>}
                </div>
            </div>
            <IconButton size="large" onClick={() => updatePeriodeMonth(1)}>
                <ArrowRightIcon fontSize="inherit"/>
            </IconButton>
        </div>
    )
}

