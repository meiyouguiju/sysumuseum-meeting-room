<script setup lang="ts">
import ReservationForm from './ReservationForm.vue'

import { useMobileBreakpoint } from '@/composables/useMobileBreakpoint'

import type { CreateBookingRequest } from '@/types/booking'
import type { ScheduleRoom } from '@/types/schedule'

defineProps<{
  date: string
  initialRoomId: number
  initialStartTime: string
  modelValue: boolean
  rooms: ScheduleRoom[]
  hasUnknownResult: boolean
  resolvingUnknownResult: boolean
  submitting: boolean
}>()
const emit = defineEmits<{
  'update:modelValue': [visible: boolean]
  cancel: []
  submit: [request: CreateBookingRequest]
  resolveUnknown: []
}>()
const { isMobile } = useMobileBreakpoint()
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    title="创建预约"
    :size="isMobile ? '100%' : '420px'"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-alert
      v-if="resolvingUnknownResult"
      title="预约请求正在处理中…"
      type="info"
      :closable="false"
      show-icon
      class="unknown-result"
    >
      <template #default>系统正在使用原幂等键确认结果，请勿再次提交。</template>
    </el-alert>
    <el-alert
      v-else-if="hasUnknownResult"
      title="预约结果暂时无法确认，请重新查询结果。"
      type="warning"
      :closable="false"
      show-icon
      class="unknown-result"
    />
    <ReservationForm
      :date="date"
      :initial-room-id="initialRoomId"
      :initial-start-time="initialStartTime"
      :rooms="rooms"
      :submitting="submitting || resolvingUnknownResult || hasUnknownResult"
      @cancel="emit('cancel')"
      @submit="emit('submit', $event)"
    />
    <el-button
      v-if="resolvingUnknownResult || hasUnknownResult"
      class="retry-result"
      :loading="resolvingUnknownResult"
      :disabled="resolvingUnknownResult"
      @click="emit('resolveUnknown')"
      >重新查询结果</el-button
    >
  </el-drawer>
</template>

<style scoped>
.unknown-result {
  margin-bottom: 16px;
}
.retry-result {
  margin-top: 12px;
}
@media (max-width: 760px) {
  :deep(.el-drawer__body) {
    padding: 16px;
  }
}
</style>
