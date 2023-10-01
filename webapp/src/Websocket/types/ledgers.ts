import {InformationElement} from "./InformationElement";
import {AccessLevel} from "./user";


export interface LedgerView extends InformationElement {
    bookingsCounter: number;
    accessLevel: AccessLevel;
}