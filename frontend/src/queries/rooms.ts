import { queryOptions } from '@tanstack/vue-query'

import { getRooms } from '@/api/rooms'

export const roomsQueryKey = ['rooms'] as const

export function roomsQueryOptions() {
  return queryOptions({ queryKey: roomsQueryKey, queryFn: getRooms, staleTime: 60_000 })
}
