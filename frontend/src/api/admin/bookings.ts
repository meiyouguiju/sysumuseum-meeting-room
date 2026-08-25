import { http } from '@/api/http'
import type { BookingDetail } from '@/types/booking'
import type {
  AdminBookingUpdateRequest,
  AdminBookingsPageResponse,
  AdminCancelBookingRequest,
  AdminCancelBookingResponse,
} from '@/types/admin'

export async function getAdminBookings(page: number, size: number) {
  const response = await http.get<AdminBookingsPageResponse>('/admin/bookings', {
    params: { page, size },
  })
  return response.data
}

export async function updateAdminBooking(id: number, request: AdminBookingUpdateRequest) {
  const response = await http.patch<BookingDetail>(`/admin/bookings/${id}`, request)
  return response.data
}

export async function cancelAdminBooking(id: number, request: AdminCancelBookingRequest) {
  const response = await http.post<AdminCancelBookingResponse>(
    `/admin/bookings/${id}/cancel`,
    request,
  )
  return response.data
}

export async function exportAdminBookings(fromDate?: string, toDate?: string) {
  const response = await http.get<Blob>('/admin/bookings/export', {
    params: { ...(fromDate ? { fromDate } : {}), ...(toDate ? { toDate } : {}) },
    responseType: 'blob',
  })
  return response
}
