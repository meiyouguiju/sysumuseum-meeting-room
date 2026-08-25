<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'

import { cancelAdminBooking, exportAdminBookings, updateAdminBooking } from '@/api/admin/bookings'
import { getBookingDetail } from '@/api/bookings'
import AdminBookingEditForm from '@/components/admin/AdminBookingEditForm.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import { adminBookingsQueryOptions } from '@/queries/adminBookings'
import { bookingDetailQueryKey, bookingDetailQueryOptions } from '@/queries/bookings'
import { currentUserQueryOptions } from '@/queries/currentUser'
import { roomsQueryOptions } from '@/queries/rooms'
import { scheduleQueryKey } from '@/queries/schedule'
import type {
  AdminBookingUpdateRequest,
  AdminInProgressBookingUpdateRequest,
  AdminUpcomingBookingUpdateRequest,
} from '@/types/admin'
import { ApiError } from '@/types/api'
import type { BookingDetail } from '@/types/booking'
import { formatTimeRange } from '@/utils/schedule'

const page = ref(1)
const size = 20
const selectedId = ref<number>()
const editSnapshot = ref<BookingDetail>()
const isEditing = ref(false)
const isMutating = ref(false)
const exportVisible = ref(false)
const exportForm = reactive({ fromDate: '', toDate: '' })
const queryClient = useQueryClient()
const listQuery = useQuery(computed(() => adminBookingsQueryOptions(page.value, size)))
const roomsQuery = useQuery(roomsQueryOptions())
const currentUserQuery = useQuery(currentUserQueryOptions())
const detailQuery = useQuery({
  queryKey: computed(() => bookingDetailQueryKey(selectedId.value ?? 0)),
  queryFn: () => getBookingDetail(selectedId.value!),
  enabled: computed(() => selectedId.value !== undefined),
})
const booking = computed(() => detailQuery.data.value)
const isOwnBooking = computed(() => booking.value?.organizer.id === currentUserQuery.data.value?.id)
const visible = computed({
  get: () => selectedId.value !== undefined,
  set: (value) => {
    if (!value) closeDrawer()
  },
})

function cloneBooking(value: BookingDetail): BookingDetail {
  return { ...value, room: { ...value.room }, organizer: { ...value.organizer } }
}
function clearEditSnapshot() {
  editSnapshot.value = undefined
  isEditing.value = false
}
function closeDrawer() {
  clearEditSnapshot()
  selectedId.value = undefined
}
function canEdit(value: BookingDetail) {
  return value.status === 'ACTIVE' && value.displayStatus !== 'ENDED'
}
function statusText(value: BookingDetail) {
  return value.status === 'CANCELLED'
    ? '已取消'
    : { UPCOMING: '未开始', IN_PROGRESS: '进行中', ENDED: '已结束' }[value.displayStatus]
}
function openDetail(id: number) {
  selectedId.value = id
}
async function openEdit(id: number) {
  selectedId.value = id
  const latest = await queryClient.fetchQuery(bookingDetailQueryOptions(id))
  if (selectedId.value === id) {
    editSnapshot.value = cloneBooking(latest)
    isEditing.value = true
  }
}
function beginEdit() {
  if (booking.value) {
    editSnapshot.value = cloneBooking(booking.value)
    isEditing.value = true
  }
}
async function invalidateData(id: number, oldDate: string, newDate?: string) {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: ['admin-bookings'] }),
    queryClient.invalidateQueries({ queryKey: ['my-bookings'] }),
    queryClient.invalidateQueries({ queryKey: bookingDetailQueryKey(id) }),
    queryClient.invalidateQueries({ queryKey: scheduleQueryKey(oldDate) }),
    ...(newDate && newDate !== oldDate
      ? [queryClient.invalidateQueries({ queryKey: scheduleQueryKey(newDate) })]
      : []),
  ])
}
function isUpcomingRequest(
  request: AdminBookingUpdateRequest,
): request is AdminUpcomingBookingUpdateRequest {
  return 'roomId' in request
}
async function handleError(error: unknown, date: string) {
  const apiError = error as ApiError
  if (apiError.errorCode === 'BOOKING_VERSION_CONFLICT') {
    const bookingId = selectedId.value

    closeDrawer()

    if (bookingId !== undefined) {
      await invalidateData(bookingId, date)
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
  }
  ElMessage.error(apiError.message ?? '操作失败，请稍后重试。')
  if (apiError.errorCode === 'BOOKING_SLOT_CONFLICT')
    await queryClient.invalidateQueries({ queryKey: scheduleQueryKey(date) })
  if (
    ['BOOKING_ALREADY_STARTED', 'BOOKING_ALREADY_ENDED', 'BOOKING_ALREADY_CANCELLED'].includes(
      apiError.errorCode ?? '',
    )
  ) {
    clearEditSnapshot()
    await Promise.all([detailQuery.refetch(), invalidateData(selectedId.value!, date)])
  }
}
async function saveEdit(request: AdminBookingUpdateRequest) {
  if (!editSnapshot.value) return
  const original = editSnapshot.value
  isMutating.value = true
  try {
    const body: AdminBookingUpdateRequest = isUpcomingRequest(request)
      ? { ...request, version: original.version }
      : { ...(request as AdminInProgressBookingUpdateRequest), version: original.version }
    const updated = await updateAdminBooking(original.id, body)
    ElMessage.success('预约已修改')
    await invalidateData(
      original.id,
      original.startTime.slice(0, 10),
      updated.startTime.slice(0, 10),
    )
    closeDrawer()
  } catch (error) {
    await handleError(error, original.startTime.slice(0, 10))
  } finally {
    isMutating.value = false
  }
}
async function cancelBooking() {
  if (!booking.value) return
  const value = booking.value
  const own = value.organizer.id === currentUserQuery.data.value?.id
  const runningHint =
    value.displayStatus === 'IN_PROGRESS'
      ? '\n该预约正在进行。取消后，当前 30 分钟时间槽仍继续占用，后续尚未开始的时间槽将释放。'
      : ''
  try {
    const result = await ElMessageBox.prompt(
      `${value.subject}\n预约人：${value.organizer.displayName}\n${value.room.name}\n${value.startTime.slice(0, 10)} ${formatTimeRange(value.startTime, value.endTime)}${runningHint}`,
      '确定取消该预约吗？',
      {
        inputPlaceholder: own ? '取消原因（可选）' : '取消他人预约必须填写原因',
        inputValidator: (reason) => (own || reason.trim() ? true : '取消他人预约必须填写原因。'),
        confirmButtonText: '确认取消',
        cancelButtonText: '暂不取消',
        type: 'warning',
      },
    )
    isMutating.value = true
    await cancelAdminBooking(value.id, {
      version: value.version,
      reason: result.value.trim() || null,
    })
    ElMessage.success('预约已取消')
    await invalidateData(value.id, value.startTime.slice(0, 10))
    closeDrawer()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close')
      await handleError(error, value.startTime.slice(0, 10))
  } finally {
    isMutating.value = false
  }
}
async function exportCsv() {
  if (exportForm.fromDate && exportForm.toDate && exportForm.fromDate > exportForm.toDate) {
    ElMessage.error('开始日期不能晚于结束日期。')
    return
  }
  try {
    const response = await exportAdminBookings(
      exportForm.fromDate || undefined,
      exportForm.toDate || undefined,
    )
    const url = URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = url
    link.download =
      response.headers['content-disposition']?.match(/filename="?([^";]+)"?/i)?.[1] ??
      'booking-records.csv'
    link.click()
    URL.revokeObjectURL(url)
    exportVisible.value = false
    ElMessage.success('CSV 已开始下载')
  } catch (error) {
    ElMessage.error((error as ApiError).message ?? '导出失败，请稍后重试。')
  }
}
</script>

<template>
  <section class="admin-page">
    <div class="page-title">
      <h1>预约管理</h1>
      <el-button type="primary" @click="exportVisible = true">导出 CSV</el-button>
    </div>
    <LoadingState v-if="listQuery.isPending.value" /><ErrorState
      v-else-if="listQuery.isError.value"
      :error="listQuery.error.value"
      @retry="listQuery.refetch()"
    /><template v-else-if="listQuery.data.value"
      ><el-table :data="listQuery.data.value.items"
        ><el-table-column prop="bookingNo" label="预约号" min-width="180" /><el-table-column
          prop="roomName"
          label="会议室"
          min-width="150"
        /><el-table-column prop="subject" label="会议主题" min-width="160" /><el-table-column
          prop="organizerName"
          label="预约人"
          width="120"
        /><el-table-column label="日期" width="120"
          ><template #default="{ row }">{{ row.startTime.slice(0, 10) }}</template></el-table-column
        ><el-table-column label="时间" width="150"
          ><template #default="{ row }">{{
            formatTimeRange(row.startTime, row.endTime)
          }}</template></el-table-column
        ><el-table-column label="状态" width="100"
          ><template #default="{ row }">{{ statusText(row) }}</template></el-table-column
        ><el-table-column label="操作" width="190"
          ><template #default="{ row }"
            ><el-button link type="primary" @click="openDetail(row.id)">查看</el-button
            ><el-button
              v-if="row.status === 'ACTIVE' && row.displayStatus !== 'ENDED'"
              link
              type="primary"
              @click="openEdit(row.id)"
              >修改</el-button
            ><el-button
              v-if="row.status === 'ACTIVE' && row.displayStatus !== 'ENDED'"
              link
              type="danger"
              @click="openDetail(row.id)"
              >取消</el-button
            ></template
          ></el-table-column
        ></el-table
      ><el-pagination
        v-if="listQuery.data.value.total > size"
        class="pagination"
        layout="prev, pager, next"
        :current-page="page"
        :page-size="size"
        :total="listQuery.data.value.total"
        @current-change="page = $event" /></template
    ><el-drawer v-model="visible" :title="isEditing ? '修改预约' : '预约详情'" size="480px"
      ><LoadingState v-if="detailQuery.isPending.value" /><ErrorState
        v-else-if="detailQuery.isError.value"
        :error="detailQuery.error.value"
        @retry="detailQuery.refetch()"
      /><AdminBookingEditForm
        v-else-if="isEditing && editSnapshot"
        :booking="editSnapshot"
        :is-own-booking="isOwnBooking"
        :rooms="roomsQuery.data.value ?? []"
        :submitting="isMutating"
        @cancel="clearEditSnapshot"
        @submit="saveEdit"
      /><template v-else-if="booking"
        ><el-descriptions :column="1" border
          ><el-descriptions-item label="预约号">{{ booking.bookingNo }}</el-descriptions-item
          ><el-descriptions-item label="会议室">{{ booking.room.name }}</el-descriptions-item
          ><el-descriptions-item label="预约人">{{
            booking.organizer.displayName
          }}</el-descriptions-item
          ><el-descriptions-item label="会议主题">{{ booking.subject }}</el-descriptions-item
          ><el-descriptions-item label="预计人数">{{
            booking.attendeeCount ?? '未填写'
          }}</el-descriptions-item
          ><el-descriptions-item label="参会人员">{{
            booking.participantsText ?? '未填写'
          }}</el-descriptions-item
          ><el-descriptions-item label="说明">{{
            booking.description ?? '未填写'
          }}</el-descriptions-item
          ><el-descriptions-item label="时间"
            >{{ booking.startTime.slice(0, 10) }}
            {{ formatTimeRange(booking.startTime, booking.endTime) }}</el-descriptions-item
          ><el-descriptions-item label="状态">{{ statusText(booking) }}</el-descriptions-item
          ><el-descriptions-item v-if="booking.cancelledAt" label="取消信息"
            >{{ booking.cancelledAt }} {{ booking.cancelReason ?? '' }}</el-descriptions-item
          ></el-descriptions
        >
        <div class="actions">
          <el-button v-if="canEdit(booking)" type="primary" @click="beginEdit">修改预约</el-button
          ><el-button
            v-if="canEdit(booking)"
            type="danger"
            :loading="isMutating"
            @click="cancelBooking"
            >取消预约</el-button
          >
        </div></template
      ></el-drawer
    ><el-dialog v-model="exportVisible" title="导出预约 CSV" width="420px"
      ><el-form label-position="top"
        ><el-form-item label="开始日期（可选）"
          ><el-date-picker
            v-model="exportForm.fromDate"
            type="date"
            value-format="YYYY-MM-DD" /></el-form-item
        ><el-form-item label="结束日期（可选）"
          ><el-date-picker
            v-model="exportForm.toDate"
            type="date"
            value-format="YYYY-MM-DD" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="exportVisible = false">取消</el-button
        ><el-button type="primary" @click="exportCsv">导出</el-button></template
      ></el-dialog
    >
  </section>
</template>
<style scoped>
.admin-page {
  display: grid;
  gap: 20px;
}
.page-title,
.actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
h1 {
  margin: 0;
}
.pagination {
  justify-content: flex-end;
  margin-top: 16px;
}
.actions {
  justify-content: flex-end;
  margin-top: 20px;
}
</style>
