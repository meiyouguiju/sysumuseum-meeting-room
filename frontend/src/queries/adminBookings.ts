import { queryOptions } from '@tanstack/vue-query'

import { getAdminBookings } from '@/api/admin/bookings'
import type { AdminBookingsParams } from '@/types/admin'

export const adminBookingsQueryKey = (page: number, size: number, filters: AdminBookingsParams) =>
  [
    'admin-bookings',
    page,
    size,
    filters.organizerKeyword,
    filters.fromDate,
    filters.toDate,
    filters.status,
  ] as const

export const adminBookingsQueryOptions = (
  page: number,
  size: number,
  filters: AdminBookingsParams,
) =>
  queryOptions({
    queryKey: adminBookingsQueryKey(page, size, filters),
    queryFn: () => getAdminBookings(page, size, filters),
  })
