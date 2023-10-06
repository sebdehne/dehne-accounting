import Header from "../Header";
import React, {useCallback, useEffect, useState} from "react";
import {Button, Container, FormControl, TextField} from "@mui/material";
import {useGlobalState} from "../../utils/userstate";
import {useNavigate, useParams, useSearchParams} from "react-router-dom";
import WebsocketService from "../../Websocket/websocketClient";
import {CategoryDto} from "../../Websocket/types/categories";
import {v4 as uuidv4} from "uuid";
import DeleteIcon from "@mui/icons-material/Delete";
import {CategorySearchBox2} from "../CategorySearchBox/CategorySearchBox2";


export const AddOrEditCategory = () => {
    const {userState, categoriesAsList} = useGlobalState();
    const {editCategoryId} = useParams();
    const [searchParams] = useSearchParams();

    const requestedParentCategoryId = editCategoryId ? undefined : searchParams.get("parent") ?? undefined;

    const [editCategory, setEditCategory] = useState<CategoryDto | undefined>(undefined);
    const [name, setName] = useState('');
    const [parentCategoryId, setParentCategoryId] = useState<string | undefined>(requestedParentCategoryId);
    const [mergeIntoCategoryId, setMergeIntoCategoryId] = useState<string | undefined>(undefined);

    const navigate = useNavigate();

    useEffect(() => {
        if (userState && !userState.ledgerId) {
            navigate("/ledger");
        }
    }, [userState, navigate]);

    useEffect(() => {
        if (!editCategory) {
            const toBeEdited = categoriesAsList.find(c => c.id === editCategoryId);
            if (toBeEdited) {
                setEditCategory(toBeEdited);
                setName(toBeEdited.name);
                setParentCategoryId(toBeEdited.parentCategoryId ?? '');
            }
        }
    }, [editCategoryId, categoriesAsList, editCategory, setEditCategory, setName, setParentCategoryId]);

    const saveAndExit = useCallback(() => {
        if (userState?.ledgerId) {
            const id = editCategory?.id || uuidv4();
            WebsocketService.rpc({
                type: "addOrReplaceCategory",
                addOrReplaceCategory: {
                    id,
                    name,
                    description: undefined,
                    parentCategoryId: !!parentCategoryId ? parentCategoryId : undefined,
                    ledgerId: userState.ledgerId
                }
            }).then(() => navigate('/categories', {replace: true}))
        }
    }, [userState, editCategory, name, parentCategoryId, navigate]);

    const mergeAndExit = useCallback(() => {
        if (userState?.ledgerId && editCategory?.id && mergeIntoCategoryId) {
            WebsocketService.rpc({
                type: "mergeCategories",
                ledgerId: userState.ledgerId,
                mergeCategoriesRequest: {
                    destinationCategoryId: mergeIntoCategoryId,
                    sourceCategoryId: editCategory.id
                }
            }).then(() => navigate('/categories'))
        }
    }, [userState, editCategory, mergeIntoCategoryId, navigate]);

    return (
        <Container maxWidth="xs" className="App">
            <Header
                title={(editCategory ? "Edit: " : "Add: ") + name}
            />

            <div style={{margin: '20px 0px 20px 0px'}}>
                <FormControl sx={{m: 1, width: '100%'}}>
                    <TextField value={name || ''}
                               label="Name"
                               onChange={event => setName(event.target.value ?? '')}
                               fullWidth={true}
                    />
                </FormControl>
            </div>

            {editCategory && <CategorySearchBox2
                exclude={editCategoryId ? [editCategoryId] : []}
                includeIntermediate={true}
                onSelectedCategoryId={s => setParentCategoryId(s)}
                value={parentCategoryId}
                title={"Parent category"}
            />}
            {!editCategory && <CategorySearchBox2
                exclude={editCategoryId ? [editCategoryId] : []}
                includeIntermediate={true}
                onSelectedCategoryId={s => setParentCategoryId(s)}
                value={parentCategoryId}
                title={"No parent category"}
            />}

            <div style={{marginTop: '20px'}}>
                <Button disabled={!name} variant={"contained"}
                        onClick={saveAndExit}>{editCategory ? 'Save & exit' : 'Add & exit'}</Button>
            </div>

            {editCategory &&
                <div>
                    <h4>Merge into</h4>
                    <CategorySearchBox2
                        exclude={[editCategory.id]}
                        includeIntermediate={true}
                        onSelectedCategoryId={s => setMergeIntoCategoryId(s)}
                        value={mergeIntoCategoryId}
                        title={"Merge destination"}
                    />
                    <div style={{marginTop: '20px'}}>
                        <Button disabled={!mergeIntoCategoryId} variant={"contained"}
                                onClick={mergeAndExit}><DeleteIcon/>Merge</Button>
                    </div>
                </div>
            }


        </Container>
    )
}