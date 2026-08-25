import { http } from '@/api/http'
import type { AdminRoomResponse, CreateRoomRequest, UpdateRoomRequest } from '@/types/admin'

export async function createRoom(request: CreateRoomRequest) {
  const response = await http.post<AdminRoomResponse>('/admin/rooms', request)
  return response.data
}

export async function updateRoom(id: number, request: UpdateRoomRequest) {
  const response = await http.patch<AdminRoomResponse>(`/admin/rooms/${id}`, request)
  return response.data
}

export async function enableRoom(id: number) {
  const response = await http.post<AdminRoomResponse>(`/admin/rooms/${id}/enable`)
  return response.data
}

export async function disableRoom(id: number) {
  const response = await http.post<AdminRoomResponse>(`/admin/rooms/${id}/disable`)
  return response.data
}
