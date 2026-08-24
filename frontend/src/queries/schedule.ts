import { queryOptions } from '@tanstack/vue-query'

import { getSchedule } from '@/api/schedule'

export function scheduleQueryKey(date: string) {
  return ['schedule', date] as const
}

export function scheduleQueryOptions(date: string) {
  return queryOptions({
    queryKey: scheduleQueryKey(date),
    queryFn: () => getSchedule(date),
    staleTime: 60_000,
  })
}
