import {InformationElement} from "./InformationElement";


export type User = InformationElement & {
    userEmail: string;
    active: boolean;
    admin: boolean;
    realmIdToAccessLevel: {[key: string]: RealmAccessLevel};
}

export type UserInfo = {
    isAdmin: boolean;
    accessibleRealms: RealmInfoWithAccessLevel[];
}

export type RealmInfoWithAccessLevel = InformationElement & {
    accessLevel: RealmAccessLevel;
}

export type RealmAccessLevel = 'read' | 'readWrite' | 'owner';

