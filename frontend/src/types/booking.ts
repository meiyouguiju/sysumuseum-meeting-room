export interface CreateBookingRequest {
  roomId: number
  subject: string
  startTime: string
  endTime: string
  attendeeCount?: number | null
  participantsText?: string | null
  description?: string | null
}

export interface BookingWarning {
  code: string
  message: string
}

export interface CreateBookingResponse {
  id: number
  bookingNo: string
  room: { id: number; name: string }
  organizer: { id: number; displayName: string }
  subject: string
  attendeeCount: number | null
  participantsText: string | null
  description: string | null
  startTime: string
  endTime: string
  status: 'ACTIVE'
  version: number
  createdAt: string
  warnings: BookingWarning[]
}

export interface IdempotencyResultResponse {
  status: 'PROCESSING' | 'SUCCEEDED' | 'FAILED'
  originalHttpStatus?: number
  failureCode?: string
  response?: CreateBookingResponse | { errorCode?: string; message?: string }
}

export interface BookingDetail {
  id: number
  bookingNo: string
  room: { id: number; name: string }
  organizer: { id: number; displayName: string }
  subject: string
  attendeeCount: number | null
  participantsText: string | null
  description: string | null
  startTime: string
  endTime: string
  status: 'ACTIVE' | 'CANCELLED'
  displayStatus: 'UPCOMING' | 'IN_PROGRESS' | 'ENDED'
  version: number
  cancelledAt: string | null
  cancelReason: string | null
  createdAt: string
  updatedAt: string
}

export interface MyBookingsPageResponse {
  items: BookingDetail[]
  page: number
  size: number
  total: number
  totalPages: number
}
export interface UpdateBookingRequest extends CreateBookingRequest {
  version: number
}
export interface CancelBookingRequest {
  version: number
  reason?: string | null
}
export interface CancelBookingResponse {
  id: number
  status: 'CANCELLED'
  version: number
  cancelledAt: string
  slotRelease: { mode: string; heldSlotStart: string | null; releasedFrom: string | null }
}
