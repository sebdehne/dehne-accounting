import {InformationElement} from "./InformationElement";


export interface UserView extends InformationElement {
    userEmail: string;
    active: boolean;
    isAdmin: boolean;
}

export type  AccessLevel = 'admin' | 'legderOwner' | 'legderReadWrite' | 'legderRead' | 'none';
