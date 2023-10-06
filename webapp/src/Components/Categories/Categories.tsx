import Header from "../Header";
import {Button, Container, FormControl, TextField} from "@mui/material";
import React, {useEffect, useMemo, useState} from "react";
import {useGlobalState} from "../../utils/userstate";
import {useNavigate} from "react-router-dom";
import {CategoryTree} from "../CategorySearchBox/CategoryTree";
import './Categories.css'
import AddIcon from "@mui/icons-material/Add";
import EditIcon from "@mui/icons-material/Edit";

export const Categories = () => {
    const {userState, ledger, setUserState, categoriesAsTree} = useGlobalState();
    const [filter, setFilter] = useState('');
    const navigate = useNavigate();

    useEffect(() => {
        if (userState && !userState.ledgerId) {
            navigate('/ledger', {replace: true});
        }
    }, [userState, navigate]);

    const onHeaderClick = () => {
        setUserState(prev => ({
            ...prev,
            ledgerId: undefined
        }))
    }

    const visibleCategoryIds: string[] = useMemo(() => {
        const ids: string[] = [];

        if (!filter) return [];

        const matches = (c: CategoryTree): boolean => {
            let myMatches = c.name.toLowerCase().includes(filter.toLowerCase());

            c.children.forEach(c => myMatches = matches(c) || myMatches);

            if (myMatches) {
                ids.unshift(c.id);
            }

            return myMatches;
        }

        categoriesAsTree.forEach(c => matches(c));

        return ids;
    }, [categoriesAsTree, filter]);

    return (
        <Container maxWidth="xs" className="App">
            <Header
                title={"Categories: " + ledger?.name ?? ""}
                clickable={onHeaderClick}
            />

            <div style={{display: "flex", flexDirection: "row"}}>
                <FormControl sx={{m: 1, width: '100%'}}>
                    <TextField value={filter}
                               label="Filter"
                               onChange={event => setFilter(event.target.value ?? '')}
                    />
                </FormControl>
                <Button onClick={() => navigate('/category')}><AddIcon/>Add root</Button>
            </div>

            <div>

            </div>

            {categoriesAsTree && <ul className="CategoryNodeUl">
                {categoriesAsTree
                    .filter(c => visibleCategoryIds.length === 0 || visibleCategoryIds.includes(c.id))
                    .map(c => (
                        <CategoryViewer category={c}
                                        visibleCategoryIds={visibleCategoryIds}
                                        key={c.id}
                        />))}
            </ul>}

        </Container>
    )
}

type CategoryViewerProps = {
    category: CategoryTree;
    visibleCategoryIds: string[];
}
const CategoryViewer = ({category, visibleCategoryIds}: CategoryViewerProps) => {
    const navigate = useNavigate();
    const [showEdit, setShowEdit] = useState(false);

    return (
        <li>
            <div className="CategoryNode" onClick={() => setShowEdit(!showEdit)}>
                <div>
                    {category.name}
                </div>
                {showEdit && <div>
                    <Button onClick={() => navigate('/category/' + category.id)}><EditIcon/></Button>
                    <Button onClick={() => navigate('/category?parent=' + category.id)}><AddIcon/></Button>
                </div>}

            </div>
            {category.children && <ul className="CategoryNodeUl">
                {category.children
                    .filter(c => visibleCategoryIds.length === 0 || visibleCategoryIds.includes(c.id))
                    .map(c => (<CategoryViewer category={c} key={c.id}
                                               visibleCategoryIds={visibleCategoryIds}/>))}
            </ul>}
        </li>
    )
}

