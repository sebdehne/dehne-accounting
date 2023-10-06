import {useMemo} from "react";
import {Autocomplete, FormControl, TextField} from "@mui/material";
import {useGlobalState} from "../../utils/userstate";
import {CategoryTree} from "./CategoryTree";


export type CategorySearchBoxProps2 = {
    includeIntermediate: boolean;
    onSelectedCategoryId: (categoryId: string | undefined) => void;
    title?: string;
    value: string | undefined;
    exclude?: string[];
    allowEmpty?: boolean;
}

export const CategorySearchBox2 = ({
                                       includeIntermediate,
                                       onSelectedCategoryId,
                                       value,
                                       title = "Search category",
                                       exclude = [],
                                       allowEmpty = false,
                                   }: CategorySearchBoxProps2) => {
    const {categoriesAsTree} = useGlobalState();
    const searchList = useMemo(() =>
            buildSearchList(categoriesAsTree, includeIntermediate).filter(c => !exclude.includes(c.category.id)),
        [categoriesAsTree, includeIntermediate, exclude]
    );

    return (<div>
        <FormControl sx={{m: 1, width: '100%'}}>
            <Autocomplete
                disableClearable={!allowEmpty}
                onChange={(_, value) => onSelectedCategoryId(
                    !!value ? (value as SearchableCategory).category.id : undefined
                )}
                options={searchList}
                value={searchList.find(c => c.category.id === value)}
                getOptionLabel={(option) => (option as SearchableCategory).parentsString + ' -> ' + (option as SearchableCategory).category.name}
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
            /></FormControl>
    </div>);
}


export type SearchableCategory = {
    category: CategoryTree;
    parentsString: string;
}

export const buildSearchList = (tree: CategoryTree[], includeIntermediate: boolean): SearchableCategory[] => {
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
