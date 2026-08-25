<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'

import { cancelMyBooking, getBookingDetail, updateMyBooking } from '@/api/bookings'
import ErrorState from '@/components/common/ErrorState.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import ReservationForm from '@/components/reservation/ReservationForm.vue'
import { bookingDetailQueryKey, bookingDetailQueryOptions, myBookingsQueryOptions } from '@/queries/bookings'
import { roomsQueryOptions } from '@/queries/rooms'
import { scheduleQueryKey } from '@/queries/schedule'
import { ApiError } from '@/types/api'
import type { BookingDetail, CreateBookingRequest } from '@/types/booking'
import { formatTimeRange } from '@/utils/schedule'

const page = ref(1)
const size = 20
const selectedId = ref<number>()
const isEditing = ref(false)
const isMutating = ref(false)
const editSnapshot = ref<BookingDetail>()
const queryClient = useQueryClient()
const listQuery = useQuery(computed(() => myBookingsQueryOptions(page.value, size)))
const roomsQuery = useQuery(roomsQueryOptions())
const detailQuery = useQuery({ queryKey: computed(() => bookingDetailQueryKey(selectedId.value ?? 0)), queryFn: () => getBookingDetail(selectedId.value!), enabled: computed(() => selectedId.value !== undefined) })
const visible = computed({ get: () => selectedId.value !== undefined, set: (value) => { if (!value) closeDrawer() } })
const booking = computed(() => detailQuery.data.value)

function canEdit(value: BookingDetail) { return value.status === 'ACTIVE' && value.displayStatus === 'UPCOMING' }
function canCancel(value: BookingDetail) { return value.status === 'ACTIVE' && value.displayStatus !== 'ENDED' }
function statusText(value: BookingDetail) { return value.status === 'CANCELLED' ? '已取消' : ({ UPCOMING: '未开始', IN_PROGRESS: '进行中', ENDED: '已结束' }[value.displayStatus]) }
function openDetail(id: number) { selectedId.value = id }
function cloneBooking(value: BookingDetail): BookingDetail { return { ...value, room: { ...value.room }, organizer: { ...value.organizer } } }
function clearEditSnapshot() { editSnapshot.value = undefined; isEditing.value = false }
function closeDrawer() { clearEditSnapshot(); selectedId.value = undefined }
function beginEdit() {
  if (!booking.value) return
  editSnapshot.value = cloneBooking(booking.value)
  isEditing.value = true
}
async function openEdit(id: number) {
  selectedId.value = id
  const latest = await queryClient.fetchQuery(bookingDetailQueryOptions(id))
  if (selectedId.value !== id) return
  editSnapshot.value = cloneBooking(latest)
  isEditing.value = true
}
async function refreshRelated(date: string, id: number) { await Promise.all([queryClient.invalidateQueries({ queryKey: ['my-bookings'] }), queryClient.invalidateQueries({ queryKey: bookingDetailQueryKey(id) }), queryClient.invalidateQueries({ queryKey: scheduleQueryKey(date) })]) }
async function handleError(error: unknown, date: string) {
  const apiError = error as ApiError
  if (apiError.errorCode === 'BOOKING_VERSION_CONFLICT') {
    const bookingId = selectedId.value

    closeDrawer()

    if (bookingId !== undefined) {
      await refreshRelated(date, bookingId)
    }

    await ElMessageBox.alert(
        '该预约已被其他操作修改，你本次修改未保存。请基于最新信息重新进入修改。',
        '修改失败',
        {
          confirmButtonText: '确定',
          type: 'warning',
        },
    )

    return
  } else {
    ElMessage.error(apiError.message ?? '操作失败，请稍后重试。')
    if (apiError.errorCode === 'BOOKING_SLOT_CONFLICT') await queryClient.invalidateQueries({ queryKey: scheduleQueryKey(date) })
    if (apiError.errorCode === 'BOOKING_ALREADY_ENDED' || apiError.errorCode === 'BOOKING_ALREADY_CANCELLED') await Promise.all([detailQuery.refetch(), refreshRelated(date, selectedId.value!)])
  }
}
async function saveEdit(request: CreateBookingRequest) {
  if (!editSnapshot.value) return
  const original = editSnapshot.value
  isMutating.value = true
  try {
    const updated = await updateMyBooking(original.id, { ...request, version: original.version })
    ElMessage.success('预约已修改')
    await Promise.all([refreshRelated(original.startTime.slice(0, 10), original.id), queryClient.invalidateQueries({ queryKey: scheduleQueryKey(updated.startTime.slice(0, 10)) })])
    closeDrawer()
  } catch (error) { await handleError(error, original.startTime.slice(0, 10)) } finally { isMutating.value = false }
}
async function cancelBooking() {
  if (!booking.value) return
  const value = booking.value
  const extra = value.displayStatus === 'IN_PROGRESS' ? '\n该预约正在进行。取消后，当前 30 分钟时间槽仍继续占用，后续尚未开始的时间槽将释放。' : ''
  try {
    await ElMessageBox.confirm(`${value.subject}\n${value.room.name}\n${value.startTime.slice(0, 10)} ${formatTimeRange(value.startTime, value.endTime)}${extra}`, '确定取消该预约吗？', { confirmButtonText: '确认取消', cancelButtonText: '暂不取消', type: 'warning' })
    isMutating.value = true
    await cancelMyBooking(value.id, { version: value.version })
    ElMessage.success('预约已取消')
    await refreshRelated(value.startTime.slice(0, 10), value.id)
  } catch (error) { if (error !== 'cancel' && error !== 'close') await handleError(error, value.startTime.slice(0, 10)) } finally { isMutating.value = false }
}
</script>

<template>
  <section class="reservations-page">
    <h1>我的预约</h1>
    <LoadingState v-if="listQuery.isPending.value" />
    <ErrorState v-else-if="listQuery.isError.value" :error="listQuery.error.value" @retry="listQuery.refetch()" />
    <el-empty v-else-if="listQuery.data.value?.items.length === 0" description="暂无预约" />
    <template v-else-if="listQuery.data.value">
      <el-table :data="listQuery.data.value.items">
        <el-table-column prop="subject" label="会议主题" min-width="180" />
        <el-table-column label="会议室" min-width="150"><template #default="{ row }">{{ row.room.name }}</template></el-table-column>
        <el-table-column label="日期" width="120"><template #default="{ row }">{{ row.startTime.slice(0, 10) }}</template></el-table-column>
        <el-table-column label="时间" width="150"><template #default="{ row }">{{ formatTimeRange(row.startTime, row.endTime) }}</template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="{ row }">{{ statusText(row) }}</template></el-table-column>
        <el-table-column label="操作" width="190"><template #default="{ row }"><el-button link type="primary" @click="openDetail(row.id)">查看</el-button><el-button v-if="canEdit(row)" link type="primary" @click="openEdit(row.id)">修改</el-button><el-button v-if="canCancel(row)" link type="danger" @click="openDetail(row.id)">取消</el-button></template></el-table-column>
      </el-table>
      <el-pagination v-if="listQuery.data.value.total > size" class="pagination" layout="prev, pager, next" :current-page="page" :page-size="size" :total="listQuery.data.value.total" @current-change="page = $event" />
    </template>
    <el-drawer v-model="visible" :title="isEditing ? '修改预约' : '预约详情'" size="440px">
      <LoadingState v-if="detailQuery.isPending.value" />
      <ErrorState v-else-if="detailQuery.isError.value" :error="detailQuery.error.value" @retry="detailQuery.refetch()" />
      <ReservationForm v-else-if="editSnapshot && isEditing" mode="edit" :date="editSnapshot.startTime.slice(0, 10)" :initial-room-id="editSnapshot.room.id" :initial-start-time="editSnapshot.startTime.slice(11, 16)" :initial-booking="editSnapshot" :rooms="roomsQuery.data.value ?? []" :submitting="isMutating" @cancel="clearEditSnapshot" @submit="saveEdit" />
      <template v-else-if="booking">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="预约号">{{ booking.bookingNo }}</el-descriptions-item><el-descriptions-item label="会议室">{{ booking.room.name }}</el-descriptions-item><el-descriptions-item label="预约人">{{ booking.organizer.displayName }}</el-descriptions-item><el-descriptions-item label="会议主题">{{ booking.subject }}</el-descriptions-item><el-descriptions-item label="预计人数">{{ booking.attendeeCount ?? '未填写' }}</el-descriptions-item><el-descriptions-item label="参会人员">{{ booking.participantsText ?? '未填写' }}</el-descriptions-item><el-descriptions-item label="说明">{{ booking.description ?? '未填写' }}</el-descriptions-item><el-descriptions-item label="时间">{{ booking.startTime.slice(0, 10) }} {{ formatTimeRange(booking.startTime, booking.endTime) }}</el-descriptions-item><el-descriptions-item label="状态">{{ statusText(booking) }}</el-descriptions-item><el-descriptions-item v-if="booking.cancelledAt" label="取消信息">{{ booking.cancelledAt }} {{ booking.cancelReason ?? '' }}</el-descriptions-item>
        </el-descriptions>
        <div class="actions"><el-button v-if="canEdit(booking)" type="primary" @click="beginEdit">修改预约</el-button><el-button v-if="canCancel(booking)" type="danger" :loading="isMutating" @click="cancelBooking">取消预约</el-button></div>
      </template>
    </el-drawer>
  </section>
</template>
<style scoped>.reservations-page { display: grid; gap: 20px; } h1 { margin: 0; } .pagination { justify-content: flex-end; margin-top: 16px; } .actions { display: flex; justify-content: flex-end; gap: 12px; margin-top: 20px; }</style>
