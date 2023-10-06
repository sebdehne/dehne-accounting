import {InformationElement} from "./InformationElement";
import {AccessLevel} from "./user";


export interface LedgerView extends InformationElement {
    accessLevel: AccessLevel;
    currency: string;
}