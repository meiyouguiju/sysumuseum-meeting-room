import { http } from '@/api/http'
import type { ChangePinRequest, LoginRequest } from '@/types/auth'
import type { CurrentUser } from '@/types/user'

export async function login(request: LoginRequest): Promise<CurrentUser> {
  return (await http.post<CurrentUser>('/auth/login', request)).data
}

export async function logout(): Promise<void> {
  await http.post('/auth/logout')
}

export async function changePin(request: ChangePinRequest): Promise<void> {
  await http.patch('/me/pin', request)
}
