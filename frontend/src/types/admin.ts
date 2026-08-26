import type { BookingDetail, CancelBookingResponse, CreateBookingRequest } from '@/types/booking'

export interface AdminBookingsParams {
  organizerKeyword?: string
  date?: string
  status?: BookingDetail['displayStatus'] | 'CANCELLED'
}
import type { Room } from '@/types/room'

export interface AdminBookingListItem {
  id: number
  bookingNo: string
  roomId: number
  roomName: string
  organizerUserId: number
  organizerName: string
  subject: string
  attendeeCount: number | null
  startTime: string
  endTime: string
  status: BookingDetail['status']
  displayStatus: BookingDetail['displayStatus']
  version: number
  cancelledAt: string | null
}

export interface AdminBookingsPageResponse {
  items: AdminBookingListItem[]
  page: number
  size: number
  total: number
  totalPages: number
}

export interface AdminUpcomingBookingUpdateRequest extends CreateBookingRequest {
  version: number
  reason?: string | null
}

export interface AdminInProgressBookingUpdateRequest {
  version: number
  subject: string
  attendeeCount?: number | null
  participantsText?: string | null
  description?: string | null
  reason?: string | null
}

export type AdminBookingUpdateRequest =
  AdminUpcomingBookingUpdateRequest | AdminInProgressBookingUpdateRequest

export interface AdminCancelBookingRequest {
  version: number
  reason?: string | null
}

export type AdminCancelBookingResponse = CancelBookingResponse

export interface CreateRoomRequest {
  name: string
  location: string
  capacity: number
  facilitiesText?: string | null
  usageNotice?: string | null
  sortOrder?: number | null
}

export type UpdateRoomRequest = Partial<CreateRoomRequest>

export type AdminRoomResponse = Room
