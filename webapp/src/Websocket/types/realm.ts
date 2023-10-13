import {InformationElement} from "./InformationElement";


export interface Realm extends InformationElement {
    currency: string;
    lastBookingId: number;
}

