import { queryOptions } from '@tanstack/vue-query'

import { getCurrentUser } from '@/api/currentUser'
import { ApiError } from '@/types/api'

export const currentUserQueryKey = ['current-user'] as const

export function currentUserQueryOptions() {
  return queryOptions({
    queryKey: currentUserQueryKey,
    queryFn: getCurrentUser,
    staleTime: 60_000,
    retry: (failureCount, error) =>
      !(error instanceof ApiError && error.status === 401) && failureCount < 3,
  })
}
