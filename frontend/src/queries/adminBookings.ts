import { queryOptions } from '@tanstack/vue-query'

import { getAdminBookings } from '@/api/admin/bookings'

export const adminBookingsQueryKey = (page: number, size: number) =>
  ['admin-bookings', page, size] as const

export const adminBookingsQueryOptions = (page: number, size: number) =>
  queryOptions({
    queryKey: adminBookingsQueryKey(page, size),
    queryFn: () => getAdminBookings(page, size),
  })
