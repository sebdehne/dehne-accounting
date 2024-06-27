import {User} from "./User";
import {UserStateV2} from "./UserStateV2";
import {RealmInfo} from "./Subscription";
import {AllAccounts} from "./accounts";


export type GlobalState = {
    user: User;
    userStateV2: UserStateV2;
    globalStateForRealm?: GlobalStateForRealm;
}

export type GlobalStateForRealm = {
    selectedRealmInfo: RealmInfo;
    allAccounts: AllAccounts;
}
