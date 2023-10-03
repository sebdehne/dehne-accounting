import {useMemo} from "react";
import {CategoryTree} from "./CategoryTree";
import {Autocomplete, TextField} from "@mui/material";
import {useGlobalState} from "../../utils/userstate";


export type CategorySearchBoxProps = {
    includeIntermediate: boolean;
    onSelectedCategoryId: (category: SearchableCategory) => void;
    title?: string;
}

export const CategorySearchBox = ({
                                      includeIntermediate,
                                      onSelectedCategoryId,
                                      title = "Search category"
                                  }: CategorySearchBoxProps) => {
    const {categoriesAsTree} = useGlobalState();
    const searchList = useMemo(() =>
            buildSearchList(categoriesAsTree, includeIntermediate),
        [categoriesAsTree, includeIntermediate]
    );

    return (<div>
        <Autocomplete
            freeSolo
            onChange={(_, value) => onSelectedCategoryId((value as SearchableCategory))}
            disableClearable
            options={searchList}
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
        />
    </div>);
}

export type SearchableCategory = {
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
