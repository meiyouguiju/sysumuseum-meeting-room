import { http } from '@/api/http'
import type { CreateBookingRequest, CreateBookingResponse, IdempotencyResultResponse } from '@/types/booking'

const idempotencyHeader = (idempotencyKey: string) => ({ headers: { 'Idempotency-Key': idempotencyKey } })

export async function createBooking(request: CreateBookingRequest, idempotencyKey: string): Promise<CreateBookingResponse> {
  const response = await http.post<CreateBookingResponse>('/bookings', request, idempotencyHeader(idempotencyKey))
  return response.data
}

export async function getBookingIdempotencyResult(idempotencyKey: string): Promise<IdempotencyResultResponse> {
  const response = await http.get<IdempotencyResultResponse>('/bookings/idempotency-result', idempotencyHeader(idempotencyKey))
  return response.data
}
