import {InformationElement} from "./InformationElement";


export interface CategoryDto extends  InformationElement {
    parentCategoryId?: string;
}

