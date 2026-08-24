import { http } from '@/api/http'
import type { ScheduleResponse } from '@/types/schedule'

export async function getSchedule(date: string): Promise<ScheduleResponse> {
  const response = await http.get<ScheduleResponse>('/schedules', { params: { date } })
  return response.data
}
