import {useMemo} from "react";
import {CategoryTree} from "./CategoryTree";
import {Autocomplete, TextField} from "@mui/material";
import {useGlobalState} from "../../utils/userstate";


export type CategorySearchBoxProps = {
    includeIntermediate: boolean;
    onSelectedCategoryId: (category: SearchableCategory | undefined) => void;
    title?: string;
    defaultCategoryId: string | undefined;
    exclude?: string[];
    allowEmpty?: boolean;
}

export const CategorySearchBox = ({
                                      includeIntermediate,
                                      onSelectedCategoryId,
                                      defaultCategoryId,
                                      title = "Search category",
                                      exclude = [],
                                      allowEmpty = false,
                                  }: CategorySearchBoxProps) => {
    const {categoriesAsTree} = useGlobalState();
    const searchList = useMemo(() =>
            buildSearchList(categoriesAsTree, includeIntermediate).filter(c => !exclude.includes(c.category.id)),
        [categoriesAsTree, includeIntermediate]
    );

    return (<div>
        <Autocomplete
            disableClearable={!allowEmpty}
            onChange={(_, value) => onSelectedCategoryId(
                !!value ? (value as SearchableCategory) : undefined
            )}
            defaultValue={searchList.find(s => s.category.id === defaultCategoryId)}
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
