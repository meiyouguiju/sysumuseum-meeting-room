import { http } from '@/api/http'
import type {
  CancelBookingRequest,
  CancelBookingResponse,
  CreateBookingRequest,
  CreateBookingResponse,
  BookingDetail,
  IdempotencyResultResponse,
  MyBookingsPageResponse,
  UpdateBookingRequest,
} from '@/types/booking'

const idempotencyHeader = (idempotencyKey: string) => ({
  headers: { 'Idempotency-Key': idempotencyKey },
})

export async function createBooking(
  request: CreateBookingRequest,
  idempotencyKey: string,
): Promise<CreateBookingResponse> {
  const response = await http.post<CreateBookingResponse>(
    '/bookings',
    request,
    idempotencyHeader(idempotencyKey),
  )
  return response.data
}

export async function getBookingIdempotencyResult(
  idempotencyKey: string,
): Promise<IdempotencyResultResponse> {
  const response = await http.get<IdempotencyResultResponse>(
    '/bookings/idempotency-result',
    idempotencyHeader(idempotencyKey),
  )
  return response.data
}

export async function getMyBookings(page: number, size: number): Promise<MyBookingsPageResponse> {
  return (await http.get<MyBookingsPageResponse>('/me/bookings', { params: { page, size } })).data
}
export async function getBookingDetail(bookingId: number): Promise<BookingDetail> {
  return (await http.get<BookingDetail>(`/bookings/${bookingId}`)).data
}
export async function updateMyBooking(
  bookingId: number,
  request: UpdateBookingRequest,
): Promise<BookingDetail> {
  return (await http.patch<BookingDetail>(`/bookings/${bookingId}`, request)).data
}
export async function cancelMyBooking(
  bookingId: number,
  request: CancelBookingRequest,
): Promise<CancelBookingResponse> {
  return (await http.post<CancelBookingResponse>(`/bookings/${bookingId}/cancel`, request)).data
}
