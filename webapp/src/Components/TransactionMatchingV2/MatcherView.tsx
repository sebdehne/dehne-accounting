import {
    AccountAction, AmountBetween, AndFilters, ContainsFilter, EndsWith, OrFilter, StartsWith,
    UnbookedBankTransactionMatcher,
    UnbookedTransactionMatcherFilter
} from "../../Websocket/types/unbookedTransactions";
import React from "react";
import {useGlobalState} from "../../utils/userstate";
import {BorderedSection} from "../borderedSection/borderedSection";
import {Amount} from "../Amount";
import "./MatcherView.css"

export type MatcherViewProps = {
    matcher: UnbookedBankTransactionMatcher,
    buttons: React.ReactNode
}
export const MatcherView = ({matcher, buttons}: MatcherViewProps) => {
    const {accounts} = useGlobalState()

    if (!accounts.hasData()) return null;

    const account = accounts.getById(matcher.actionAccountId)

    const type = matcher.action["@c"] === ".TransferAction"
        ? "Transfer"
        : (matcher.action as AccountAction).type === "accountsPayable"
            ? "Payment"
            : "Income";

    const mainAccount = matcher.action["@c"] === ".AccountAction"
        ? accounts.getById((matcher.action as AccountAction).mainAccountId!)
        : undefined;


    return <div className="MatcherView">
        <div className="MatcherViewSummary">
            <div className="MatcherViewSummaryName">{matcher.name}</div>
            {buttons}
        </div>
        <BorderedSection title={"Filter:"}>
            <FilterView filter={matcher.filter}/>
        </BorderedSection>
        <BorderedSection title={"Action: " + type}>
            <div className="MatcherViewAccount">
                <div className="MatcherViewAccountType">
                    {type == "Transfer" && "To: "}
                    {type == "Payment" && "Pay to: "}
                    {type == "Income" && "From: "}
                </div>
                <div className="MatcherViewAccountAccountName">
                    {account.name}
                </div>
            </div>
            {mainAccount && <div>
                {accounts.generateParentsString(mainAccount.id)} {'->'} {mainAccount.name}
            </div>}
        </BorderedSection>
        <div style={{marginTop: '10px'}}>
            {matcher.actionMemo && 'Memo: ' + matcher.actionMemo}
        </div>

    </div>
}


type FilterViewProps = {
    filter: UnbookedTransactionMatcherFilter
}
const FilterView = ({filter}: FilterViewProps) => {
    return <div>
        {filter["@c"] === ".ContainsFilter" && <div>Contains: '{(filter as ContainsFilter).value}'</div>}
        {filter["@c"] === ".StartsWith" && <div>Starts with: '{(filter as StartsWith).value}'</div>}
        {filter["@c"] === ".EndsWith" && <div>Ends with: '{(filter as EndsWith).value}'</div>}
        {filter["@c"] === ".AmountBetween" &&
            <div>Amount between <Amount amountInCents={(filter as AmountBetween).from}/> and <Amount
                amountInCents={(filter as AmountBetween).toExcluding}/></div>}
        {filter["@c"] === ".OrFilter" &&
            <div>Any of: {(filter as OrFilter).filters.map((f, i) => <FilterView key={i} filter={f}/>)}</div>}
        {filter["@c"] === ".AndFilters" &&
            <div>All of: {(filter as AndFilters).filters.map((f, i) => <FilterView key={i} filter={f}/>)}</div>}
    </div>
}

