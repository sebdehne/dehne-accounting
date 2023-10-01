import {InformationElement} from "../../Websocket/types/InformationElement";
import {CategoryView} from "../../Websocket/types/categories";


export interface CategoryTree extends InformationElement {
    children: CategoryTree[];
}

export const buildTree = (categories: CategoryView[]): CategoryTree[] => {

    const findChildren = (id: string) => categories.filter(c => c.parentCategoryId === id);

    const toLeaf = (c: CategoryView): CategoryTree => ({
        id: c.id,
        name: c.name,
        description: c.description,
        children: findChildren(c.id ?? '').map(child => toLeaf(child))
    })

    return categories.filter(c => !c.parentCategoryId).map(c => toLeaf(c));
}
