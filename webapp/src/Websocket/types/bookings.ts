import {CategoryDto} from "./categories";


export type GetBookingsRequest = {
    from: string;
    toExcluding: string;
    limit?: number;
}

export type BookingView = {
    ledgerId: string;
    id: number;
    description?: string;
    datetime: string;
    records: BookingRecordView[],
}


export type  BookingRecordView = {
    ledgerId: string;
    bookingId: number;
    id: number;
    description?: string;
    categoryId: string,
    amount: number;
}

