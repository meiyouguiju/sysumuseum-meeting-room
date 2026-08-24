import { http } from '@/api/http'
import type { Room } from '@/types/room'

export async function getRooms(): Promise<Room[]> {
  const response = await http.get<Room[]>('/rooms')
  return response.data
}
