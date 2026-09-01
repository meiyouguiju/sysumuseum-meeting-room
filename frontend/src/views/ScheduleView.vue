<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'

import ErrorState from '@/components/common/ErrorState.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import CreateReservationDrawer from '@/components/reservation/CreateReservationDrawer.vue'
import BookingNotificationDialog from '@/components/reservation/BookingNotificationDialog.vue'
import MyBookingDetailDrawer from '@/components/reservation/MyBookingDetailDrawer.vue'
import DesktopScheduleGrid from '@/components/schedule/DesktopScheduleGrid.vue'
import MobileScheduleTimeline from '@/components/schedule/MobileScheduleTimeline.vue'
import ScheduleToolbar from '@/components/schedule/ScheduleToolbar.vue'
import { useMobileBreakpoint } from '@/composables/useMobileBreakpoint'
import { useDrawerHistory } from '@/composables/useDrawerHistory'
import { useCreateBooking, type CreateBookingSubmission } from '@/composables/useCreateBooking'
import { scheduleQueryOptions } from '@/queries/schedule'
import { scheduleQueryKey } from '@/queries/schedule'
import type { CreateBookingRequest, CreateBookingResponse } from '@/types/booking'
import type { ScheduleBooking, ScheduleRoom } from '@/types/schedule'
import type { DaySlot } from '@/utils/schedule'
import { formatTimeRange, scheduleBookingStatusText, todayInShanghai } from '@/utils/schedule'

const selectedDate = ref(todayInShanghai())
const scheduleQuery = useQuery(computed(() => scheduleQueryOptions(selectedDate.value)))
const queryClient = useQueryClient()
const selectedBooking = ref<ScheduleBooking>()
const selectedMyBookingId = ref<number>()
const selectedEmptySlot = ref<{ room: ScheduleRoom; slot: DaySlot }>()
const selectedMobileRoomId = ref<number>()
const createdBooking = ref<CreateBookingResponse>()
const notificationVisible = computed({
  get: () => createdBooking.value !== undefined,
  set: (visible: boolean) => {
    if (!visible) createdBooking.value = undefined
  },
})
const { isMobile } = useMobileBreakpoint()
const {
  hasUnknownResult,
  isResolvingUnknownResult,
  isSubmitting,
  resetCreateFlow,
  resolveUnknownResult,
  startCreateFlow,
  submit,
} = useCreateBooking()
const drawerVisible = computed({
  get: () => selectedBooking.value !== undefined,
  set: (visible: boolean) => {
    if (!visible) {
      selectedBooking.value = undefined
    }
  },
})
const myBookingDrawerVisible = computed({
  get: () => selectedMyBookingId.value !== undefined,
  set: (visible: boolean) => {
    if (!visible) selectedMyBookingId.value = undefined
  },
})
const visibleSchedule = computed(() => {
  const schedule = scheduleQuery.data.value
  if (!schedule) return undefined

  return {
    ...schedule,
    rooms: schedule.rooms.filter((room) => room.status === 'ENABLED'),
  }
})

useDrawerHistory(drawerVisible)

const selectedRoom = computed(() => {
  if (!selectedBooking.value) {
    return undefined
  }

  return scheduleQuery.data.value?.rooms.find((room) => room.id === selectedBooking.value?.roomId)
})
const selectedMobileRoom = computed(() => {
  const rooms = visibleSchedule.value?.rooms ?? []
  return rooms.find((room) => room.id === selectedMobileRoomId.value) ?? rooms[0]
})

watch(
  () => visibleSchedule.value?.rooms,
  (rooms) => {
    if (
      rooms &&
      rooms.length > 0 &&
      !rooms.some((room) => room.id === selectedMobileRoomId.value)
    ) {
      selectedMobileRoomId.value = rooms[0]!.id
    }
  },
  { immediate: true },
)

const selectedBookingStatus = computed(() => {
  if (!selectedBooking.value) return ''
  return scheduleBookingStatusText(selectedBooking.value.displayStatus)
})

const createDrawerVisible = computed({
  get: () => selectedEmptySlot.value !== undefined,
  set: (visible: boolean) => {
    if (!visible) {
      selectedEmptySlot.value = undefined
      resetCreateFlow()
    }
  },
})

function selectEmptySlot(room: ScheduleRoom, slot: DaySlot) {
  selectedEmptySlot.value = { room, slot }
  startCreateFlow()
}
function selectBooking(booking: ScheduleBooking) {
  if (booking.isMine) {
    selectedMyBookingId.value = booking.id
    return
  }
  selectedBooking.value = booking
}

async function handleCreateSubmission(result: CreateBookingSubmission) {
  if (result.kind === 'succeeded') {
    createdBooking.value = result.response
    result.response.warnings.forEach((warning) => ElMessage.warning(warning.message))
    createDrawerVisible.value = false
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: scheduleQueryKey(selectedDate.value) }),
      queryClient.invalidateQueries({ queryKey: ['my-bookings'] }),
    ])
    return
  }

  if (result.kind === 'unknown') {
    ElMessage.warning('无法确认刚才预约请求的最终结果，请刷新日程确认后再操作。')
    return
  }

  const { error } = result
  if (error.errorCode === 'BOOKING_SLOT_CONFLICT') {
    ElMessage.error('该时间段刚刚已被其他人预约，请重新选择。')
    await queryClient.invalidateQueries({ queryKey: scheduleQueryKey(selectedDate.value) })
  } else {
    ElMessage.error(error.message)
    if (error.errorCode === 'MEETING_ROOM_DISABLED') {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['rooms'] }),
        queryClient.invalidateQueries({ queryKey: scheduleQueryKey(selectedDate.value) }),
      ])
    }
  }
  resetCreateFlow()
}

async function submitCreateBooking(request: CreateBookingRequest) {
  await handleCreateSubmission(await submit(request))
}

async function resolveCreateBookingResult() {
  await handleCreateSubmission(await resolveUnknownResult())
}
</script>

<template>
  <section class="schedule-page">
    <div class="schedule-page-header">
      <div>
        <h1>会议室日程</h1>
      </div>
      <ScheduleToolbar v-model="selectedDate" />
    </div>

    <LoadingState v-if="scheduleQuery.isPending.value" />
    <ErrorState
      v-else-if="scheduleQuery.isError.value"
      :error="scheduleQuery.error.value"
      @retry="scheduleQuery.refetch()"
    />
    <DesktopScheduleGrid
      v-else-if="visibleSchedule && !isMobile"
      :schedule="visibleSchedule"
      @select-booking="selectBooking"
      @select-empty-slot="selectEmptySlot"
    />
    <template v-else-if="visibleSchedule && selectedMobileRoom">
      <div class="mobile-room-selector">
        <label class="mobile-room-label">会议室</label>

        <el-select
          v-model="selectedMobileRoomId"
          placeholder="请选择会议室"
          class="mobile-room-select"
        >
          <el-option
            v-for="room in visibleSchedule.rooms"
            :key="room.id"
            :label="room.name"
            :value="room.id"
          />
        </el-select>

        <div v-if="selectedMobileRoom" class="mobile-room-summary">
          {{ selectedMobileRoom.capacity }} 人
        </div>
      </div>
      <MobileScheduleTimeline
        :schedule="visibleSchedule"
        :room="selectedMobileRoom"
        @select-booking="selectBooking"
        @select-empty-slot="selectEmptySlot"
      />
    </template>

    <el-drawer v-model="drawerVisible" title="预约概要" :size="isMobile ? '100%' : '360px'">
      <el-descriptions v-if="selectedBooking" :column="1" border>
        <el-descriptions-item label="会议主题">{{ selectedBooking.subject }}</el-descriptions-item>
        <el-descriptions-item label="预约人">{{
          selectedBooking.organizerName
        }}</el-descriptions-item>
        <el-descriptions-item label="会议室">{{
          selectedRoom?.name ?? '未知会议室'
        }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{
          formatTimeRange(selectedBooking.startTime, selectedBooking.endTime)
        }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ selectedBookingStatus }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
    <MyBookingDetailDrawer v-model="myBookingDrawerVisible" :booking-id="selectedMyBookingId" />
    <BookingNotificationDialog v-model="notificationVisible" :booking="createdBooking" />
    <CreateReservationDrawer
      v-if="selectedEmptySlot"
      v-model="createDrawerVisible"
      :date="selectedDate"
      :initial-room-id="selectedEmptySlot.room.id"
      :initial-start-time="selectedEmptySlot.slot.label"
      :rooms="visibleSchedule?.rooms ?? []"
      :has-unknown-result="hasUnknownResult"
      :submitting="isSubmitting"
      :resolving-unknown-result="isResolvingUnknownResult"
      @cancel="createDrawerVisible = false"
      @submit="submitCreateBooking"
      @resolve-unknown="resolveCreateBookingResult"
    />
  </section>
</template>

<style scoped>
.schedule-page {
  display: grid;
  gap: 20px;
  width: calc(100vw - 48px);
  margin-left: calc(50% - 50vw + 24px);
}
.schedule-page-header {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: center;
}
h1 {
  margin: 0 0 6px;
}
@media (max-width: 800px) {
  .schedule-page {
    width: auto;
    margin-left: 0;
    transform: none;
  }
  .schedule-page-header {
    gap: 12px;
    align-items: flex-start;
    flex-direction: column;
  }
  h1 {
    margin: 0;
    font-size: 20px;
  }
}
.mobile-room-selector {
  display: grid;
  gap: 6px;
}

.mobile-room-label {
  font-weight: 700;
}

.mobile-room-select {
  width: 100%;
}

.mobile-room-summary {
  color: #64748b;
  font-size: 14px;
}
</style>
