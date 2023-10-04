import moment from "moment";
import {CategoryTree} from "../Components/CategorySearchBox/CategoryTree";
import {CategoryView} from "../Websocket/types/categories";


export function formatLocalDate(m: moment.Moment) {
    return m.format('YYYY-MM-DD');
}

export function formatLocatDayMonth(m: moment.Moment) {
    return m.format('DD. MMM');
}

export function formatYearMonth(m: moment.Moment) {
    return m.format('MMMM, YYYY');
}
export function formatIso(m: moment.Moment) {
    return m.format();
}

export function startOfCurrentMonth(): moment.Moment {
    return moment().clone().startOf('month');
}

export function monthDelta(m: moment.Moment, delta: number): moment.Moment {
    return m.clone().add(delta, 'months');
}

export const arrayBufferToBase64 = (buffer: ArrayBuffer ) => {
    let binary = '';
    const bytes = new Uint8Array( buffer );
    const len = bytes.byteLength;
    for (let i = 0; i < len; i++) {
        binary += String.fromCharCode( bytes[ i ] );
    }
    return btoa( binary );
};

export const categoryParentsPath = (categories: CategoryView[], parentCategoryId: string |undefined): string => {
    const parts: string[] = [];
    let current = categories.find(c => c.id === parentCategoryId);
    while(current) {
        parts.unshift(current.name);
        current = categories.find(c => c.id === current?.parentCategoryId);
    }
    return parts.length > 0 ? (parts.join(" > ") + " > ") : "";
}