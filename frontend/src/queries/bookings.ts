import { queryOptions } from '@tanstack/vue-query'
import { getBookingDetail, getMyBookings } from '@/api/bookings'
import type { MyBookingsParams } from '@/types/booking'

export const myBookingsQueryKey = (page: number, size: number, filters: MyBookingsParams) =>
  ['my-bookings', page, size, filters.status, filters.date] as const
export const bookingDetailQueryKey = (bookingId: number) => ['booking-detail', bookingId] as const
export const myBookingsQueryOptions = (page: number, size: number, filters: MyBookingsParams) =>
  queryOptions({
    queryKey: myBookingsQueryKey(page, size, filters),
    queryFn: () => getMyBookings(page, size, filters),
  })
export const bookingDetailQueryOptions = (bookingId: number) =>
  queryOptions({
    queryKey: bookingDetailQueryKey(bookingId),
    queryFn: () => getBookingDetail(bookingId),
  })
