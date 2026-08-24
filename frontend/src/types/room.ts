export type RoomStatus = 'ENABLED' | 'DISABLED'

export interface Room {
  id: number
  name: string
  location: string
  capacity: number
  facilitiesText: string
  usageNotice: string
  status: RoomStatus
  sortOrder: number
}
