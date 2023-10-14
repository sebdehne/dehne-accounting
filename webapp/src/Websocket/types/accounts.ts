import {InformationElement} from "./InformationElement";


export interface AccountDto extends InformationElement {
    parentAccountId?: string;
    partyId?: string;
}