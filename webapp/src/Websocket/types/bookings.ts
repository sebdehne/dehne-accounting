import {CategoryView} from "./categories";


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
    bookingType: BookingType,
}

export type BookingType = 'payment' | 'income' | 'transfer' | 'internalBooking'


export type  BookingRecordView = {
    ledgerId: string;
    bookingId: number;
    id: number;
    description?: string;
    category: CategoryView,
    amount: number;
}

