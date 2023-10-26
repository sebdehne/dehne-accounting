import Header from "../Header";
import {Container, ListItemIcon, Menu, MenuItem} from "@mui/material";
import React, {useCallback, useEffect, useState} from "react";
import {useGlobalState} from "../../utils/userstate";
import {useNavigate} from "react-router-dom";
import {PeriodSelectorV2} from "../PeriodSelectors/PeriodSelector";
import {OverviewRapportAccount} from "../../Websocket/types/OverviewRapportAccount";
import WebsocketClient from "../../Websocket/websocketClient";
import "./RealmMain.css"
import {Amount} from "../Amount";
import ArrowRightIcon from "@mui/icons-material/ArrowRight";
import {ArrowDropDownIcon} from "@mui/x-date-pickers";
import IconButton from "@mui/material/IconButton";
import MoreVertIcon from '@mui/icons-material/MoreVert';
import {Check} from "@mui/icons-material";

export const RealmMain = () => {
    const {userStateV2, setUserStateV2, realm} = useGlobalState();
    const [overviewRapport, setOverviewRapport] = useState<OverviewRapportAccount[]>();
    const [totalUnbookedTransactions, setTotalUnbookedTransactions] = useState(0);

    const navigate = useNavigate();

    useEffect(() => {
        if (userStateV2 && !userStateV2.selectedRealm) {
            navigate('/realm', {replace: true});
        }
    }, [userStateV2, navigate]);

    const onHeaderClick = () => {
        setUserStateV2(prev => ({
            ...prev,
            selectedRealm: undefined
        }))
    }

    useEffect(() => {
        const sub = WebsocketClient.subscribe(
            {type: "getOverviewRapport"},
            readResponse => setOverviewRapport(readResponse.overViewRapport)
        )
        return () => WebsocketClient.unsubscribe(sub);
    }, [setOverviewRapport]);
    useEffect(() => {
        WebsocketClient.subscribe(
            {type: "getTotalUnbookedTransactions"},
            readResponse => setTotalUnbookedTransactions(readResponse.totalUnbookedTransactions!)
        )
    }, [setTotalUnbookedTransactions]);

    return (
        <Container maxWidth="xs" className="App">
            <Header
                title={realm?.name ?? ""}
                clickable={onHeaderClick}
            />

            {totalUnbookedTransactions > 0 &&
                <div className="TotalUnbookedTransactions">({totalUnbookedTransactions} unbooked transactions)</div>}

            <PeriodSelectorV2/>
            {overviewRapport && <OverviewRapportViewer overviewRapport={overviewRapport}/>}

        </Container>
    );
}

type HideSettings = {
    hideWithoutRecords: boolean;
    hideThisPeriodZero: boolean;
    hideBalanceZero: boolean;
}

type OverviewRapportViewerProps = {
    overviewRapport: OverviewRapportAccount[];
}
const OverviewRapportViewer = ({overviewRapport}: OverviewRapportViewerProps) => {
    const [hideSettings, setHideSettings] = useState<HideSettings>({
        hideBalanceZero: false,
        hideThisPeriodZero: false,
        hideWithoutRecords: true
    });

    const filter = useCallback((a: OverviewRapportAccount) => {
        if (hideSettings.hideWithoutRecords) {
            if (a.deepEntrySize === 0 && a.children.length === 0) {
                return false;
            }
        }
        if (hideSettings.hideBalanceZero && a.openBalance === 0 && a.closeBalance === 0) {
            return false;
        }
        if (hideSettings.hideThisPeriodZero && a.thisPeriod === 0) {
            return false;
        }
        return true;
    }, [hideSettings]);

    return (<div>
        <div style={{display: "flex", flexDirection: "row", justifyContent: "flex-end"}}>
            <HideSettingsMenu hideSettings={hideSettings} setHideSettings={setHideSettings}/>
        </div>

        <ul className="OverviewRapportViewerAccounts">
            {overviewRapport.filter(filter).map((a, index) => (<OverviewRapportViewerAccount
                key={a.name}
                account={a}
                level={0}
                isLast={index === overviewRapport.length - 1}
                filter={filter}
            />))}
        </ul>
    </div>)
}

type HideSettingsMenuProps = {
    hideSettings: HideSettings;
    setHideSettings: React.Dispatch<React.SetStateAction<HideSettings>>;
}
const ITEM_HEIGHT = 48;
const HideSettingsMenu = ({hideSettings, setHideSettings}: HideSettingsMenuProps) => {
    const [anchorEl, setAnchorEl] = React.useState<null | HTMLElement>(null);
    const open = Boolean(anchorEl);
    const handleClick = (event: React.MouseEvent<HTMLElement>) => {
        setAnchorEl(event.currentTarget);
    };
    const handleClose = (option?: string) => {
        setAnchorEl(null);
        if (option) {
            setHideSettings(prevState => {
                const map = prevState as any
                return ({
                    ...map,
                    [option]: !map[option]
                });
            })
        }
    };

    return (
        <div>
            <IconButton
                aria-label="more"
                id="long-button"
                aria-controls={open ? 'long-menu' : undefined}
                aria-expanded={open ? 'true' : undefined}
                aria-haspopup="true"
                onClick={handleClick}
            >
                <MoreVertIcon/>
            </IconButton>
            <Menu
                id="long-menu"
                MenuListProps={{
                    'aria-labelledby': 'long-button',
                }}
                anchorEl={anchorEl}
                open={open}
                onClose={() => handleClose()}
                PaperProps={{
                    style: {
                        maxHeight: ITEM_HEIGHT * 4.5,
                        width: '30ch',
                    },
                }}
            >

                <MenuItem onClick={() => handleClose('hideWithoutRecords')}>
                    {hideSettings.hideWithoutRecords && <ListItemIcon>
                        <Check/>
                    </ListItemIcon>}
                    Hide without records
                </MenuItem>
                <MenuItem onClick={() => handleClose('hideThisPeriodZero')}>
                    {hideSettings.hideThisPeriodZero && <ListItemIcon>
                        <Check/>
                    </ListItemIcon>}
                    Hide zero period</MenuItem>
                <MenuItem onClick={() => handleClose('hideBalanceZero')}>
                    {hideSettings.hideBalanceZero && <ListItemIcon>
                        <Check/>
                    </ListItemIcon>}
                    Hide zero balance</MenuItem>
            </Menu>
        </div>
    );
}


type OverviewRapportViewerAccountProps = {
    account: OverviewRapportAccount;
    level: number;
    isLast: boolean;
    filter: (a: OverviewRapportAccount) => boolean;
}
const OverviewRapportViewerAccount = ({account, level, isLast, filter}: OverviewRapportViewerAccountProps) => {
    const {localState, setLocalState, accounts} = useGlobalState();
    const navigate = useNavigate();

    const overviewRapportAccounts = account.children.filter(filter);

    if (!accounts.hasData()) return null;

    return (<li className="OverviewRapportViewerAccount" style={{marginLeft: (level * 5) + 'px'}}>

        <div
            className="OverviewRapportViewerAccountSummary"
        >
            <div className="OverviewRapportViewerAccountSummaryLevel">{!isLast && <MiddleLine/>}{isLast &&
                <LastLine/>}</div>
            <div className="OverviewRapportViewerAccountSummaryMain">
                <div className="OverviewRapportViewerAccountSummaryLeft">
                    {overviewRapportAccounts.length > 0 &&
                        <IconButton size={"small"} onClick={() => setLocalState(prev => ({
                            ...prev,
                            accountTree: prev.accountTree.toggle(account.accountId)
                        }))}>
                            {localState.accountTree.isExpanded(account.accountId) &&
                                <ArrowDropDownIcon fontSize={"small"}/>}
                            {!localState.accountTree.isExpanded(account.accountId) &&
                                <ArrowRightIcon fontSize={"small"}/>}
                        </IconButton>
                    }
                    {overviewRapportAccounts.length === 0 && <div style={{margin: '12px'}}></div>}
                    <div onClick={() => navigate('/bookings/' + account.accountId)}>
                        {accounts.getById(account.accountId)!.name}
                    </div>
                </div>
                <div className="OverviewRapportViewerAccountSummaryRight">
                    <div style={{fontSize: "small", color: "#a8a8a8"}}><Amount
                        amountInCents={account.openBalance}/></div>
                    <div style={{fontSize: "larger"}}><Amount amountInCents={account.thisPeriod}/></div>
                    <div style={{fontSize: "small", color: "#a8a8a8"}}><Amount
                        amountInCents={account.closeBalance}/></div>
                </div>
            </div>
        </div>

        {localState.accountTree.isExpanded(account.accountId) && <ul className="OverviewRapportViewerAccounts">
            {overviewRapportAccounts
                .map((c, index) => (<OverviewRapportViewerAccount
                    key={c.name}
                    account={c}
                    level={level + 1}
                    isLast={index === overviewRapportAccounts.length - 1}
                    filter={filter}
                />))}
        </ul>}
    </li>)
}

const lineHeight = 110;
const lineWidth = 14;
const lineColor = "#696969"

const MiddleLine = () => {
    return (<svg height={lineHeight} width={lineWidth}>
        <line x1={0} y1="0" x2={0} y2={lineHeight} style={{
            stroke: lineColor,
            strokeWidth: 2
        }}/>
        <line x1={0} y1={lineHeight / 2} x2={lineWidth} y2={lineHeight / 2} style={{
            stroke: lineColor,
            strokeWidth: 2
        }}/>
    </svg>);
}
const LastLine = () => {
    return (<svg height={lineHeight} width={lineWidth}>
        <line x1={0} y1={0} x2={0} y2={lineHeight / 2} style={{
            stroke: lineColor,
            strokeWidth: 2
        }}/>
        <line x1={0} y1={lineHeight / 2} x2={lineWidth} y2={lineHeight / 2} style={{
            stroke: lineColor,
            strokeWidth: 2
        }}/>
    </svg>);
}

