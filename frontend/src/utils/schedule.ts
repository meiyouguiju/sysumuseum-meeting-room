export interface DaySlot {
  index: number
  label: string
  minutes: number
  isHour: boolean
}

const MINUTES_PER_DAY = 24 * 60

export function buildDaySlots(slotMinutes: number): DaySlot[] {
  if (slotMinutes <= 0 || MINUTES_PER_DAY % slotMinutes !== 0) {
    throw new Error('日程时间粒度必须能整除 24 小时。')
  }

  return Array.from({ length: MINUTES_PER_DAY / slotMinutes }, (_, index) => {
    const minutes = index * slotMinutes
    const hour = Math.floor(minutes / 60)
    const minute = minutes % 60

    return {
      index,
      label: `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`,
      minutes,
      isHour: minute === 0,
    }
  })
}

export function timeToMinutes(dateTime: string): number {
  const matched = dateTime.match(/(?:T|^)(\d{2}):(\d{2})/)
  if (!matched) {
    throw new Error(`无法从时间值解析小时和分钟：${dateTime}`)
  }

  return Number(matched[1]) * 60 + Number(matched[2])
}

export function timeToSlotIndex(dateTime: string, slotMinutes: number): number {
  return Math.floor(timeToMinutes(dateTime) / slotMinutes)
}

export function calculateSlotSpan(startTime: string, endTime: string, slotMinutes: number): number {
  return Math.max(0, Math.floor((timeToMinutes(endTime) - timeToMinutes(startTime)) / slotMinutes))
}

export function formatTimeRange(startTime: string, endTime: string): string {
  return `${extractTime(startTime)}–${extractTime(endTime)}`
}

export function extractTime(dateTime: string): string {
  return dateTime.match(/(?:T|^)(\d{2}:\d{2})/)?.[1] ?? dateTime
}

export function isInFocusWindow(slot: DaySlot, start: string, end: string): boolean {
  return slot.minutes >= timeToMinutes(start) && slot.minutes < timeToMinutes(end)
}

export function todayInShanghai(): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts()
  const part = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((item) => item.type === type)?.value

  return `${part('year')}-${part('month')}-${part('day')}`
}

export function currentMinutesInShanghai(): number {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    hour: '2-digit',
    hourCycle: 'h23',
    minute: '2-digit',
  }).formatToParts()
  const part = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((item) => item.type === type)?.value

  return Number(part('hour')) * 60 + Number(part('minute'))
}

export function shiftDate(date: string, amount: number): string {
  const matched = date.match(/^(\d{4})-(\d{2})-(\d{2})$/)
  if (!matched) {
    throw new Error(`无效日期：${date}`)
  }

  const year = Number(matched[1])
  const month = Number(matched[2])
  const day = Number(matched[3])
  const value = new Date(Date.UTC(year, month - 1, day + amount))

  return value.toISOString().slice(0, 10)
}

export function isCreatableScheduleSlot(input: {
  date: string
  roomStatus: 'ENABLED' | 'DISABLED'
  slot: DaySlot
  slots: DaySlot[]
  slotMinutes: number
  bookings: Array<{ startTime: string; endTime: string }>
  unavailableSlots: Array<{ slotStart: string }>
}): boolean {
  if (input.roomStatus !== 'ENABLED' || input.slot.index === input.slots.length - 1) return false
  if (input.date < todayInShanghai()) return false
  if (input.date === todayInShanghai() && input.slot.minutes <= currentMinutesInShanghai())
    return false
  if (
    input.unavailableSlots.some(
      (slot) => timeToSlotIndex(slot.slotStart, input.slotMinutes) === input.slot.index,
    )
  )
    return false
  return !input.bookings.some((booking) => {
    const start = timeToSlotIndex(booking.startTime, input.slotMinutes)
    const end = start + calculateSlotSpan(booking.startTime, booking.endTime, input.slotMinutes)
    return input.slot.index >= start && input.slot.index < end
  })
}
