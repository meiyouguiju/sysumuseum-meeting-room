import type { BookingDetail, CreateBookingResponse } from '@/types/booking'

const WEEKDAYS = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
const TENCENT_DOC_URL = 'https://docs.qq.com/sheet/DZXRZT1Vmd0dyS1hy'

type NotificationBooking = Pick<
  CreateBookingResponse | BookingDetail,
  'room' | 'startTime' | 'endTime'
>

function formatDateAndWeekday(startTime: string): { date: string; weekday: string } {
  const matched = startTime.match(/^(\d{4})-(\d{2})-(\d{2})/)
  if (!matched) return { date: startTime, weekday: '' }

  const year = Number(matched[1])
  const month = Number(matched[2])
  const day = Number(matched[3])
  const weekday = WEEKDAYS[new Date(Date.UTC(year, month - 1, day)).getUTCDay()]!
  return { date: `${month}月${day}日`, weekday }
}

function timeOf(value: string): string {
  return value.match(/(?:T|^)(\d{2}:\d{2})/)?.[1] ?? value
}

export function buildWeChatNotification(booking: NotificationBooking): string {
  const { date, weekday } = formatDateAndWeekday(booking.startTime)
  return `各位同事，我申请预约${booking.room.name}，时间是${date}（${weekday}）${timeOf(booking.startTime)}-${timeOf(booking.endTime)}，已同步更新【腾讯文档】博物馆（校史馆）会议室预约登记。${TENCENT_DOC_URL}。`
}

export async function copyText(text: string): Promise<void> {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text)
    return
  }

  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', '')
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.append(textarea)
  textarea.select()
  const copied = document.execCommand('copy')
  textarea.remove()
  if (!copied) throw new Error('浏览器不支持复制操作。')
}
