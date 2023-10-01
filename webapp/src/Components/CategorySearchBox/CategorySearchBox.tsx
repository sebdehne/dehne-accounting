import {useEffect, useState} from "react";
import {buildTree, CategoryTree} from "./CategoryTree";
import WebsocketClient from "../../Websocket/websocketClient";
import {Autocomplete, TextField} from "@mui/material";


export type CategorySearchBoxProps = {
    includeIntermediate: boolean;
    onSelectedCategoryId: (categoryId: string) => void;
}

export const CategorySearchBox = ({includeIntermediate, onSelectedCategoryId}: CategorySearchBoxProps) => {
    const [tree, setTree] = useState<CategoryTree[]>([]);
    const [searchList, setSearchList] = useState<SearchableCategory[]>([]);

    useEffect(() => {
        const subId = WebsocketClient.subscribe(
            {type: "allCategories"},
            notify => {
                const tree = buildTree(notify.readResponse.categories!);
                setTree(tree);
                setSearchList(buildSearchList(tree, includeIntermediate));
            }
        )

        return () => WebsocketClient.unsubscribe(subId);
    }, [includeIntermediate]);

    return (<div>
        <Autocomplete
            freeSolo
            onChange={(_, value) => onSelectedCategoryId((value as SearchableCategory).category.id)}
            disableClearable
            options={searchList}
            getOptionLabel={(option) => (option as SearchableCategory).parentsString + ' -> ' + (option as SearchableCategory).category.name}
            renderInput={(params) => (
                <TextField
                    {...params}
                    label="Search category"
                    InputProps={{
                        ...params.InputProps,
                        type: 'search',
                    }}
                />
            )}
        />
    </div>);
}

type SearchableCategory = {
    category: CategoryTree;
    parentsString: string;
}

const buildSearchList = (tree: CategoryTree[], includeIntermediate: boolean): SearchableCategory[] => {
    let result: SearchableCategory[] = [];

    const visit = (t: CategoryTree, parentNames: string[]) => {
        if (t.children.length === 0 || includeIntermediate) {
            result.push({
                category: t,
                parentsString: parentNames.join(":")
            });
        }
        t.children.forEach(c => visit(c, [...parentNames, t.name]));
    }

    tree.map(t => visit(t, []));

    return result;
}
