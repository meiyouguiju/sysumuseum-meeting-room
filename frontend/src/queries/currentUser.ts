import { queryOptions } from '@tanstack/vue-query'

import { getCurrentUser } from '@/api/currentUser'

export const currentUserQueryKey = ['current-user'] as const

export function currentUserQueryOptions() {
  return queryOptions({ queryKey: currentUserQueryKey, queryFn: getCurrentUser, staleTime: 60_000 })
}
