<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'

import {
  cancelMyBooking,
  getBookingDetail,
  updateBookingSupplementalInfo,
  updateMyBooking,
} from '@/api/bookings'
import { copyText, buildWeChatNotification } from '@/utils/wechatNotification'
import ErrorState from '@/components/common/ErrorState.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import ReservationForm from '@/components/reservation/ReservationForm.vue'
import SupplementalInfoForm from '@/components/reservation/SupplementalInfoForm.vue'
import { useDrawerHistory } from '@/composables/useDrawerHistory'
import { useMobileBreakpoint } from '@/composables/useMobileBreakpoint'
import { bookingDetailQueryKey } from '@/queries/bookings'
import { roomsQueryOptions } from '@/queries/rooms'
import { scheduleQueryKey } from '@/queries/schedule'
import { ApiError } from '@/types/api'
import type { BookingDetail, CreateBookingRequest, SupplementalInfoRequest } from '@/types/booking'
import { formatTimeRange } from '@/utils/schedule'
import { displayOptionalDetailValue } from '@/utils/bookingDetail'

const props = defineProps<{ modelValue: boolean; bookingId?: number; editOnOpen?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [visible: boolean] }>()

const isEditing = ref(false)
const isSupplementing = ref(false)
const isMutating = ref(false)
const editSnapshot = ref<BookingDetail>()
const supplementalSnapshot = ref<BookingDetail>()
const { isMobile } = useMobileBreakpoint()
const queryClient = useQueryClient()
const roomsQuery = useQuery(roomsQueryOptions())
const detailQuery = useQuery({
  queryKey: computed(() => bookingDetailQueryKey(props.bookingId ?? 0)),
  queryFn: () => getBookingDetail(props.bookingId!),
  enabled: computed(() => props.modelValue && props.bookingId !== undefined),
})
const booking = computed(() => detailQuery.data.value)
const drawerVisible = computed({
  get: () => props.modelValue,
  set: (visible: boolean) => {
    if (!visible) closeDrawer()
  },
})

useDrawerHistory(drawerVisible)

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) clearSnapshots()
  },
)
watch(booking, (value) => {
  if (props.modelValue && props.editOnOpen && value && !isEditing.value) beginEdit()
})
watch(
  () => [props.modelValue, props.editOnOpen] as const,
  () => {
    if (props.modelValue && props.editOnOpen && booking.value && !isEditing.value) beginEdit()
  },
)

function cloneBooking(value: BookingDetail): BookingDetail {
  return { ...value, room: { ...value.room }, organizer: { ...value.organizer } }
}
function clearEditSnapshot() {
  editSnapshot.value = undefined
  isEditing.value = false
}
function clearSupplementalSnapshot() {
  supplementalSnapshot.value = undefined
  isSupplementing.value = false
}
function clearSnapshots() {
  clearEditSnapshot()
  clearSupplementalSnapshot()
}
function closeDrawer() {
  clearSnapshots()
  emit('update:modelValue', false)
}
function canEdit(value: BookingDetail) {
  return value.status === 'ACTIVE' && value.displayStatus === 'UPCOMING'
}
function canCancel(value: BookingDetail) {
  return value.status === 'ACTIVE' && value.displayStatus !== 'ENDED'
}
function canSupplement(value: BookingDetail) {
  return !canEdit(value)
}
function statusText(value: BookingDetail) {
  return value.status === 'CANCELLED'
    ? '已取消'
    : { UPCOMING: '未开始', IN_PROGRESS: '进行中', ENDED: '已结束' }[value.displayStatus]
}
function beginEdit() {
  if (!booking.value) return
  editSnapshot.value = cloneBooking(booking.value)
  isEditing.value = true
}
function beginSupplement() {
  if (!booking.value) return
  supplementalSnapshot.value = cloneBooking(booking.value)
  isSupplementing.value = true
}
async function copyNotification(value: BookingDetail) {
  try {
    await copyText(buildWeChatNotification(value))
    ElMessage.success('已复制，可直接粘贴到微信群')
  } catch {
    ElMessage.error('复制失败，请手动选择预约信息。')
  }
}
async function refreshRelated(date: string, id: number) {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: ['my-bookings'] }),
    queryClient.invalidateQueries({ queryKey: bookingDetailQueryKey(id) }),
    queryClient.invalidateQueries({ queryKey: scheduleQueryKey(date) }),
  ])
}
async function handleError(error: unknown, date: string, id: number) {
  const apiError = error as ApiError
  if (apiError.errorCode === 'BOOKING_VERSION_CONFLICT') {
    clearEditSnapshot()
    await refreshRelated(date, id)
    closeDrawer()
    await ElMessageBox.alert(
      '该预约已被其他操作修改，你本次修改未保存。请基于最新信息重新进入修改。',
      '修改失败',
      { confirmButtonText: '确定', type: 'warning' },
    )
    return
  }

  ElMessage.error(apiError.message ?? '操作失败，请稍后重试。')
  if (apiError.errorCode === 'BOOKING_SLOT_CONFLICT') {
    await queryClient.invalidateQueries({ queryKey: scheduleQueryKey(date) })
  }
  if (
    apiError.errorCode === 'BOOKING_ALREADY_ENDED' ||
    apiError.errorCode === 'BOOKING_ALREADY_CANCELLED'
  ) {
    clearEditSnapshot()
    await Promise.all([detailQuery.refetch(), refreshRelated(date, id)])
  }
}
async function saveEdit(request: CreateBookingRequest) {
  if (!editSnapshot.value) return
  const original = editSnapshot.value
  isMutating.value = true
  try {
    const updated = await updateMyBooking(original.id, { ...request, version: original.version })
    ElMessage.success('预约已修改')
    await Promise.all([
      refreshRelated(original.startTime.slice(0, 10), original.id),
      queryClient.invalidateQueries({ queryKey: scheduleQueryKey(updated.startTime.slice(0, 10)) }),
    ])
    closeDrawer()
  } catch (error) {
    await handleError(error, original.startTime.slice(0, 10), original.id)
  } finally {
    isMutating.value = false
  }
}
async function saveSupplementalInfo(request: Omit<SupplementalInfoRequest, 'version'>) {
  if (!supplementalSnapshot.value) return
  const original = supplementalSnapshot.value
  isMutating.value = true
  try {
    await updateBookingSupplementalInfo(original.id, { ...request, version: original.version })
    ElMessage.success('补充信息已保存')
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['my-bookings'] }),
      queryClient.invalidateQueries({ queryKey: ['admin-bookings'] }),
      queryClient.invalidateQueries({ queryKey: bookingDetailQueryKey(original.id) }),
    ])
    clearSupplementalSnapshot()
  } catch (error) {
    const apiError = error as ApiError
    if (apiError.errorCode === 'BOOKING_VERSION_CONFLICT') {
      clearSupplementalSnapshot()
      await Promise.all([
        detailQuery.refetch(),
        queryClient.invalidateQueries({ queryKey: ['my-bookings'] }),
        queryClient.invalidateQueries({ queryKey: ['admin-bookings'] }),
      ])
      await ElMessageBox.alert(
        '该预约已被其他操作修改，你本次修改未保存。请基于最新信息重新进入修改。',
        '修改失败',
        { confirmButtonText: '确定', type: 'warning' },
      )
    } else {
      ElMessage.error(apiError.message ?? '保存失败，请稍后重试。')
    }
  } finally {
    isMutating.value = false
  }
}
async function cancelBooking() {
  if (!booking.value) return
  const value = booking.value
  const runningHint =
    value.displayStatus === 'IN_PROGRESS'
      ? '\n该预约正在进行。取消后，当前 30 分钟时间槽仍继续占用，后续尚未开始的时间槽将释放。'
      : ''
  try {
    await ElMessageBox.confirm(
      `${value.subject}\n${value.room.name}\n${value.startTime.slice(0, 10)} ${formatTimeRange(value.startTime, value.endTime)}${runningHint}`,
      '确定取消该预约吗？',
      { confirmButtonText: '确认取消', cancelButtonText: '暂不取消', type: 'warning' },
    )
    isMutating.value = true
    await cancelMyBooking(value.id, { version: value.version })
    ElMessage.success('预约已取消')
    await refreshRelated(value.startTime.slice(0, 10), value.id)
    closeDrawer()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      await handleError(error, value.startTime.slice(0, 10), value.id)
    }
  } finally {
    isMutating.value = false
  }
}
</script>

<template>
  <el-drawer
    v-model="drawerVisible"
    :title="isEditing ? '修改预约' : isSupplementing ? '补充预约信息' : '预约详情'"
    :size="isMobile ? '100%' : '440px'"
  >
    <LoadingState v-if="detailQuery.isPending.value" />
    <ErrorState
      v-else-if="detailQuery.isError.value"
      :error="detailQuery.error.value"
      @retry="detailQuery.refetch()"
    />
    <ReservationForm
      v-else-if="editSnapshot && isEditing"
      mode="edit"
      :date="editSnapshot.startTime.slice(0, 10)"
      :initial-room-id="editSnapshot.room.id"
      :initial-start-time="editSnapshot.startTime.slice(11, 16)"
      :initial-booking="editSnapshot"
      :rooms="roomsQuery.data.value ?? []"
      :submitting="isMutating"
      @cancel="clearEditSnapshot"
      @submit="saveEdit"
    />
    <SupplementalInfoForm
      v-else-if="supplementalSnapshot && isSupplementing"
      :booking="supplementalSnapshot"
      :submitting="isMutating"
      @cancel="clearSupplementalSnapshot"
      @submit="saveSupplementalInfo"
    />
    <template v-else-if="booking">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="预约号">{{ booking.bookingNo }}</el-descriptions-item>
        <el-descriptions-item label="会议室">{{ booking.room.name }}</el-descriptions-item>
        <el-descriptions-item label="预约人">{{
          booking.organizer.displayName
        }}</el-descriptions-item>
        <el-descriptions-item label="会议主题">{{ booking.subject }}</el-descriptions-item>
        <el-descriptions-item label="预计人数">{{
          displayOptionalDetailValue(booking.attendeeCount)
        }}</el-descriptions-item>
        <el-descriptions-item label="参会人员">{{
          displayOptionalDetailValue(booking.participantsText)
        }}</el-descriptions-item>
        <el-descriptions-item label="说明">{{
          displayOptionalDetailValue(booking.description)
        }}</el-descriptions-item>
        <el-descriptions-item label="时间"
          >{{ booking.startTime.slice(0, 10) }}
          {{ formatTimeRange(booking.startTime, booking.endTime) }}</el-descriptions-item
        >
        <el-descriptions-item label="状态">{{ statusText(booking) }}</el-descriptions-item>
        <el-descriptions-item v-if="booking.cancelledAt" label="取消信息"
          >{{ booking.cancelledAt }} {{ booking.cancelReason ?? '' }}</el-descriptions-item
        >
      </el-descriptions>
      <div class="actions">
        <el-button @click="copyNotification(booking)">复制微信群通知</el-button>
        <el-button v-if="canEdit(booking)" type="primary" @click="beginEdit">修改预约</el-button>
        <el-button v-if="canSupplement(booking)" type="primary" @click="beginSupplement"
          >保存补充信息</el-button
        >
        <el-button
          v-if="canCancel(booking)"
          type="danger"
          :loading="isMutating"
          @click="cancelBooking"
          >取消预约</el-button
        >
      </div>
    </template>
  </el-drawer>
</template>

<style scoped>
.actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 20px;
}
</style>
