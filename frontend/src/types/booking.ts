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
