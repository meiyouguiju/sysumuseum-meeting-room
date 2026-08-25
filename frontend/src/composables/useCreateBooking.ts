import { ref } from 'vue'

import { createBooking, getBookingIdempotencyResult } from '@/api/bookings'
import { ApiError } from '@/types/api'
import type {
  CreateBookingRequest,
  CreateBookingResponse,
  IdempotencyResultResponse,
} from '@/types/booking'

const MAX_RESULT_POLLS = 5
const RESULT_POLL_DELAY_MS = 1_000

export type CreateBookingSubmission =
  | { kind: 'succeeded'; response: CreateBookingResponse }
  | { kind: 'failed'; error: ApiError }
  | { kind: 'unknown' }

function newIdempotencyKey(): string {
  return crypto.randomUUID()
}

function sleep(milliseconds: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, milliseconds))
}

function resultFailure(result: IdempotencyResultResponse): ApiError {
  const response = result.response as { errorCode?: string; message?: string } | undefined
  return new ApiError({
    message: response?.message ?? '预约创建失败，请修改后重试。',
    errorCode: result.failureCode ?? response?.errorCode,
    status: result.originalHttpStatus,
  })
}

export function useCreateBooking() {
  const idempotencyKey = ref<string>()
  const isSubmitting = ref(false)
  const isResolvingUnknownResult = ref(false)
  const hasUnknownResult = ref(false)

  function startCreateFlow() {
    idempotencyKey.value = newIdempotencyKey()
    hasUnknownResult.value = false
  }

  function resetCreateFlow() {
    idempotencyKey.value = undefined
    isResolvingUnknownResult.value = false
    hasUnknownResult.value = false
  }

  async function resolveUnknownResult(): Promise<CreateBookingSubmission> {
    if (!idempotencyKey.value) {
      return { kind: 'unknown' }
    }

    isResolvingUnknownResult.value = true
    hasUnknownResult.value = false
    try {
      for (let attempt = 0; attempt < MAX_RESULT_POLLS; attempt += 1) {
        try {
          const result = await getBookingIdempotencyResult(idempotencyKey.value)
          if (result.status === 'SUCCEEDED' && result.response) {
            return { kind: 'succeeded', response: result.response as CreateBookingResponse }
          }
          if (result.status === 'FAILED') {
            return { kind: 'failed', error: resultFailure(result) }
          }
        } catch (error) {
          const apiError = error as ApiError
          if (apiError.status === 404) {
            hasUnknownResult.value = true
            return { kind: 'unknown' }
          }
        }

        if (attempt < MAX_RESULT_POLLS - 1) {
          await sleep(RESULT_POLL_DELAY_MS)
        }
      }
      hasUnknownResult.value = true
      return { kind: 'unknown' }
    } finally {
      isResolvingUnknownResult.value = false
    }
  }

  async function submit(request: CreateBookingRequest): Promise<CreateBookingSubmission> {
    if (hasUnknownResult.value) {
      return { kind: 'unknown' }
    }
    if (!idempotencyKey.value) {
      startCreateFlow()
    }

    isSubmitting.value = true
    try {
      const response = await createBooking(request, idempotencyKey.value!)
      return { kind: 'succeeded', response }
    } catch (error) {
      const apiError = error as ApiError
      if (!apiError.status || apiError.errorCode === 'IDEMPOTENCY_PROCESSING') {
        return resolveUnknownResult()
      }
      return { kind: 'failed', error: apiError }
    } finally {
      isSubmitting.value = false
    }
  }

  return {
    hasUnknownResult,
    isResolvingUnknownResult,
    isSubmitting,
    resetCreateFlow,
    resolveUnknownResult,
    startCreateFlow,
    submit,
  }
}
