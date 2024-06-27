import {InformationElement} from "./InformationElement";


export type User = InformationElement & {
    userEmail: string;
    active: boolean;
    admin: boolean;
    realmIdToAccessLevel: {[key: string]: RealmAccessLevel};
}

export type RealmAccessLevel = 'read' | 'readWrite' | 'owner';

