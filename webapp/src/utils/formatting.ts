import dayjs from "dayjs";

export function formatLocalDate(m: dayjs.Dayjs) {
    return m.format('YYYY-MM-DD');
}

export function formatLocalDayMonth(m: dayjs.Dayjs) {
    return m.format('DD. MMM');
}

export function formatLocalDayMonthYear(m: dayjs.Dayjs) {
    return m.format('DD. MMM YYYY');
}

export function formatYearMonth(m: dayjs.Dayjs) {
    return m.format('MMMM, YYYY');
}
export function formatMonth(m: dayjs.Dayjs) {
    return m.format('MMMM');
}

export function formatYear(m: dayjs.Dayjs) {
    return m.format('YYYY');
}
export function formatIso(m: dayjs.Dayjs) {
    return m.format();
}

export function startOfCurrentMonth(): dayjs.Dayjs {
    return dayjs().clone().startOf('month');
}

export function monthDelta(m: dayjs.Dayjs, delta: number): dayjs.Dayjs {
    return m.clone().add(delta, "months");
}
export function yearDelta(m: dayjs.Dayjs, delta: number): dayjs.Dayjs {
    return m.clone().add(delta, "years");
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


export const amountInCentsToString = (amountInCentsToString: number, locale: string = "nb-NO") => {
    const formated = new Intl.NumberFormat(locale, {
        style: 'currency',
        currency: 'NOK',
        maximumFractionDigits: 2,
    })
        .format((amountInCentsToString) / 100)

    // remove the currency symbol
    const symbol = formated.split(" ", 1)[0];
    return formated.replace(symbol + " ", "");
}

export function toColor(num: number) {
    num >>>= 0;
    var b = num & 0xFF,
        g = (num & 0xFF00) >>> 8,
        r = (num & 0xFF0000) >>> 16;
    return "rgb(" + [r, g, b].join(",") + ")";
}

