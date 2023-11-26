import {InformationElement} from "./InformationElement";


export type User = InformationElement & {
    userEmail: string;
    active: boolean;
    admin: boolean;
    realmIdToAccessLevel: Map<String, RealmAccessLevel>,
}

export type UserInfo = {
    isAdmin: boolean;
    accessibleRealms: RealmInfo[];
}

export type RealmInfo = InformationElement & {
    accessLevel: RealmAccessLevel;
}

export type RealmAccessLevel = 'read' | 'readWrite' | 'owner';

