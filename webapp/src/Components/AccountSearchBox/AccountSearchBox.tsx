import {AccountDto} from "../../Websocket/types/accounts";
import {useGlobalState} from "../../utils/userstate";
import {useMemo} from "react";
import {Autocomplete, FormControl, TextField} from "@mui/material";
import {AccountExpanded} from "../../utils/accounts";


export type AccountSearchBoxProps = {
    onSelectedAccountId: (accountId: string | undefined) => void;
    title?: string;
    value: string | undefined;
    exclude?: string[];
    includeStartsWithPath?: string[][];
}


export const AccountSearchBox = ({
                                     onSelectedAccountId,
                                     value,
                                     title = "Select account",
                                     exclude = [],
                                     includeStartsWithPath = [],
                                 }: AccountSearchBoxProps) => {

    const {accounts} = useGlobalState();

    const currentValue = useMemo(() => {
        let result = value ? accounts.getByIdExpanded(value) : undefined;
        if (!result) {
            if (accounts.hasData()) {
                result = accounts.flat[0];
            } else {
                result = dummyItem;
            }
        }

        return result;
    }, [value, accounts]);

    if (currentValue === dummyItem) return null;

    const options = accounts.flat
        .filter(a => includeStartsWithPath?.length === 0 || includeStartsWithPath.some(path => a.startsWith(path)))
        .sort((a, b) => a.compare(b))
    ;

    return (<FormControl sx={{m: 0, width: '100%'}}>
        <Autocomplete
            isOptionEqualToValue={(left, right) => left.account.id === right.account.id}
            disableClearable={false}
            onChange={(_, value) => onSelectedAccountId(
                value ? value.account.id : undefined
            )}
            options={options}
            value={currentValue}
            getOptionLabel={(option) => accounts.generateParentsString((option as AccountExpanded).account.id) + ' -> ' + (option as AccountExpanded).account.name}
            renderInput={(params) => (
                <TextField
                    {...params}
                    label={title}
                    InputProps={{
                        ...params.InputProps,
                        type: 'search',
                    }}
                />
            )}
        /></FormControl>);

}

const dummyItem = new AccountExpanded(
    {
        id: 'dummy',
        name: 'dummy',
    },
    [],
    []
)

export type AccountDtoTree = {
    account: AccountDto;
    children: AccountDtoTree[];
    parentPath: AccountDto[];
}

export type SearchableAccount = {
    account: AccountDtoTree;
    parentsString: string;
}

export const buildSearchList = (tree: AccountDtoTree[], includeIntermediate: boolean = true): SearchableAccount[] => {
    let result: SearchableAccount[] = [];

    const visit = (t: AccountDtoTree, parentNames: string[]) => {
        if (t.children.length === 0 || includeIntermediate) {
            result.push({
                account: t,
                parentsString: parentNames.join(":")
            });
        }
        t.children.forEach(c => visit(c, [...parentNames, t.account.name]));
    }

    tree.map(t => visit(t, []));

    return result;
}
