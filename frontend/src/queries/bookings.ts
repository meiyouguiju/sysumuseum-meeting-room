import { queryOptions } from '@tanstack/vue-query'
import { getBookingDetail, getMyBookings } from '@/api/bookings'

export const myBookingsQueryKey = (page: number, size: number) => ['my-bookings', page, size] as const
export const bookingDetailQueryKey = (bookingId: number) => ['booking-detail', bookingId] as const
export const myBookingsQueryOptions = (page: number, size: number) => queryOptions({ queryKey: myBookingsQueryKey(page, size), queryFn: () => getMyBookings(page, size) })
export const bookingDetailQueryOptions = (bookingId: number) => queryOptions({ queryKey: bookingDetailQueryKey(bookingId), queryFn: () => getBookingDetail(bookingId) })
