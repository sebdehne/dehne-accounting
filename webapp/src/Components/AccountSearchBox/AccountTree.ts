import {AccountDto} from "../../Websocket/types/accounts";
import {AccountDtoTree} from "./AccountSearchBox";


export const buildTree = (accounts: AccountDto[]): AccountDtoTree[] => {

    const findChildren = (id: string) => accounts.filter(a => a.parentAccountId === id);

    const toLeaf = (a: AccountDto, parentPath: AccountDto[]): AccountDtoTree => ({
        account: a,
        children: findChildren(a.id ?? '').map(child => toLeaf(child, [...parentPath, child])),
        parentPath
    })

    return accounts.filter(c => !c.parentAccountId).map(c => toLeaf(c, []));
}
