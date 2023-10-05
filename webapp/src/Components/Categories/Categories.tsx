import Header from "../Header";
import {Button, Container, FormControl, TextField} from "@mui/material";
import React, {useEffect, useMemo, useState} from "react";
import {useGlobalState} from "../../utils/userstate";
import WebsocketService from "../../Websocket/websocketClient";
import {useNavigate} from "react-router-dom";
import {LedgerView} from "../../Websocket/types/ledgers";
import {CategoryTree} from "../CategorySearchBox/CategoryTree";
import './Categories.css'
import AddIcon from "@mui/icons-material/Add";
import EditIcon from "@mui/icons-material/Edit";

export const Categories = () => {
    const {userState, setUserState, categoriesAsTree} = useGlobalState();
    const [ledger, setLedger] = useState<LedgerView>();
    const [filter, setFilter] = useState('');

    const navigate = useNavigate();

    useEffect(() => {
        if (userState?.ledgerId) {
            const subId = WebsocketService.subscribe(
                {type: "getLedgers"},
                n => setLedger(n.readResponse.ledgers?.find(l => l.id === userState.ledgerId))
            );

            return () => WebsocketService.unsubscribe(subId);
        } else if (userState && !userState.ledgerId) {
            navigate('/ledger', {replace: true});
        }
    }, [setLedger, userState, navigate]);

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
        <Container maxWidth="sm" className="App">
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

