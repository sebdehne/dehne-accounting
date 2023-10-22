

export function removeItemWithSlice<T>(list: T[], index: number): T[] {
    return [...list.slice(0, index), ...list.slice(index + 1)];
}

export const clone = <T> (v: T): T => JSON.parse(JSON.stringify(v)) as T