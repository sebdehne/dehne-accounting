import {InformationElement} from "./InformationElement";


export interface CategoryDto extends  InformationElement {
    parentCategoryId?: string;
    ledgerId: string;
}

export type MergeCategoriesRequest = {
    sourceCategoryId: string;
    destinationCategoryId: string;
}

