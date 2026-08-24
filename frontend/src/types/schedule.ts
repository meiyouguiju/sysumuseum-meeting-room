import type { RoomStatus } from '@/types/room'

export interface FocusWindow {
  start: string
  end: string
}

export interface ScheduleRoom {
  id: number
  name: string
  status: RoomStatus
  capacity: number
}

export type ScheduleBookingDisplayStatus = 'UPCOMING' | 'IN_PROGRESS' | 'ENDED'

export interface ScheduleBooking {
  id: number
  roomId: number
  subject: string
  organizerName: string
  startTime: string
  endTime: string
  displayStatus: ScheduleBookingDisplayStatus
}

export interface UnavailableSlot {
  roomId: number
  slotStart: string
  reason: 'CANCELLED_CURRENT_SLOT_HOLD'
}

export interface ScheduleResponse {
  date: string
  timeZone: string
  slotMinutes: number
  focusWindow: FocusWindow
  rooms: ScheduleRoom[]
  bookings: ScheduleBooking[]
  unavailableSlots: UnavailableSlot[]
}
