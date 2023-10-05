import {useMemo} from "react";
import {Autocomplete, TextField} from "@mui/material";
import {useGlobalState} from "../../utils/userstate";
import {buildSearchList, SearchableCategory} from "./CategorySearchBox";


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
        [categoriesAsTree, includeIntermediate]
    );

    return (<div>
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
        />
    </div>);
}

