import { http } from '@/api/http'
import type { LoginRequest } from '@/types/auth'
import type { CurrentUser } from '@/types/user'

export async function login(request: LoginRequest): Promise<CurrentUser> {
  return (await http.post<CurrentUser>('/auth/login', request)).data
}

export async function logout(): Promise<void> {
  await http.post('/auth/logout')
}
