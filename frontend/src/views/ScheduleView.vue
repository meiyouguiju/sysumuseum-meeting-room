<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'

import ErrorState from '@/components/common/ErrorState.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import DesktopScheduleGrid from '@/components/schedule/DesktopScheduleGrid.vue'
import ScheduleToolbar from '@/components/schedule/ScheduleToolbar.vue'
import { scheduleQueryOptions } from '@/queries/schedule'
import type { ScheduleBooking } from '@/types/schedule'
import { formatTimeRange, todayInShanghai } from '@/utils/schedule'

const selectedDate = ref(todayInShanghai())
const scheduleQuery = useQuery(computed(() => scheduleQueryOptions(selectedDate.value)))
const selectedBooking = ref<ScheduleBooking>()
const drawerVisible = computed({
  get: () => selectedBooking.value !== undefined,
  set: (visible: boolean) => {
    if (!visible) {
      selectedBooking.value = undefined
    }
  },
})

const selectedRoom = computed(() => {
  if (!selectedBooking.value) {
    return undefined
  }

  return scheduleQuery.data.value?.rooms.find((room) => room.id === selectedBooking.value?.roomId)
})

const selectedBookingStatus = computed(() => {
  const status = selectedBooking.value?.displayStatus
  return status === 'UPCOMING' ? '未开始' : status === 'IN_PROGRESS' ? '进行中' : '已结束'
})
</script>

<template>
  <section class="schedule-page">
    <div class="schedule-page-header">
      <div>
        <h1>会议室日程</h1>
        <p>查看全天会议室安排；重点展示时段由服务端配置。</p>
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
      v-else-if="scheduleQuery.data.value"
      :schedule="scheduleQuery.data.value"
      @select-booking="selectedBooking = $event"
    />

    <el-drawer v-model="drawerVisible" title="预约概要" size="360px">
      <el-descriptions v-if="selectedBooking" :column="1" border>
        <el-descriptions-item label="会议主题">{{ selectedBooking.subject }}</el-descriptions-item>
        <el-descriptions-item label="预约人">{{ selectedBooking.organizerName }}</el-descriptions-item>
        <el-descriptions-item label="会议室">{{ selectedRoom?.name ?? '未知会议室' }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ formatTimeRange(selectedBooking.startTime, selectedBooking.endTime) }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ selectedBookingStatus }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </section>
</template>

<style scoped>
.schedule-page { display: grid; gap: 20px; width: calc(100vw - 48px); margin-left: calc(50% - 50vw + 24px); }
.schedule-page-header { display: flex; justify-content: space-between; gap: 20px; align-items: center; }
h1 { margin: 0 0 6px; }
p { margin: 0; color: #64748b; }
@media (max-width: 800px) { .schedule-page { width: auto; margin-left: 0; transform: none; } .schedule-page-header { align-items: flex-start; flex-direction: column; } }
</style>
