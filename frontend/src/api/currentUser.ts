import { http } from '@/api/http'
import type { CurrentUser } from '@/types/user'

export async function getCurrentUser(): Promise<CurrentUser> {
  const response = await http.get<CurrentUser>('/me')
  return response.data
}
