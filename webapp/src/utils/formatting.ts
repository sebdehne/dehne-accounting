import moment from "moment";
import {CategoryDto} from "../Websocket/types/categories";


export function formatLocalDate(m: moment.Moment) {
    return m.format('YYYY-MM-DD');
}

export function formatLocatDayMonth(m: moment.Moment) {
    return m.format('DD. MMM');
}

export function formatYearMonth(m: moment.Moment) {
    return m.format('MMMM, YYYY');
}
export function formatMonth(m: moment.Moment) {
    return m.format('MMMM');
}

export function formatYear(m: moment.Moment) {
    return m.format('YYYY');
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
export function yearDelta(m: moment.Moment, delta: number): moment.Moment {
    return m.clone().add(delta, 'years');
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

export const categoryParentsPath = (categories: CategoryDto[], parentCategoryId: string |undefined, endDelimiter: boolean = true): string => {
    const parts: string[] = [];
    /* eslint-disable no-loop-func */
    let current = categories.find(c => c.id === parentCategoryId);
    while(current) {
        parts.unshift(current.name);
        current = categories.find(c => c.id === current?.parentCategoryId);
    }
    return parts.length > 0 ? (parts.join(":") + (endDelimiter ? " > " : "")) : "";
}

export const amountInCentsToString = (amountInCentsToString: number, locale: string, currency: string) => {
    const formated = new Intl.NumberFormat(locale, {
        style: 'currency',
        currency: currency,
        maximumFractionDigits: 2,
    })
        .format((amountInCentsToString) / 100)

    // remove the currency symbol
    const symbol = formated.split(" ", 1)[0];
    return formated.replace(symbol + " ", "");
}