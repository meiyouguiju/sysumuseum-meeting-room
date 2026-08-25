<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'

import type { CreateBookingRequest } from '@/types/booking'
import type { ScheduleRoom } from '@/types/schedule'
import { shiftDate, todayInShanghai } from '@/utils/schedule'

const props = defineProps<{
  date: string
  initialRoomId: number
  initialStartTime: string
  rooms: ScheduleRoom[]
  submitting: boolean
}>()
const emit = defineEmits<{
  cancel: []
  submit: [request: CreateBookingRequest]
}>()

const timeOptions = Array.from({ length: 48 }, (_, index) => {
  const minutes = index * 30
  return `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`
})
const form = reactive({
  roomId: props.initialRoomId,
  subject: '',
  startTime: props.initialStartTime,
  endTime: nextHalfHour(props.initialStartTime),
  attendeeCount: undefined as number | undefined,
  participantsText: '',
  description: '',
})

const today = todayInShanghai()
const lastBookableDate = shiftDate(today, 13)
const selectedRoom = computed(() => props.rooms.find((room) => room.id === form.roomId))
const isCapacityExceeded = computed(() => form.attendeeCount !== undefined && form.attendeeCount > (selectedRoom.value?.capacity ?? Infinity))

watch(() => props.initialRoomId, (roomId) => {
  form.roomId = roomId
})
watch(() => props.initialStartTime, (startTime) => {
  form.startTime = startTime
  form.endTime = nextHalfHour(startTime)
})

function nextHalfHour(time: string): string {
  const index = timeOptions.indexOf(time)
  return timeOptions[Math.min(index + 1, timeOptions.length - 1)] ?? time
}

function toDateTime(time: string): string {
  return `${props.date}T${time}:00`
}

function submit() {
  const subject = form.subject.trim()
  const startIndex = timeOptions.indexOf(form.startTime)
  const endIndex = timeOptions.indexOf(form.endTime)
  const durationMinutes = (endIndex - startIndex) * 30

  if (!subject) {
    ElMessage.error('请输入会议主题。')
    return
  }
  if (endIndex <= startIndex) {
    ElMessage.error('结束时间必须晚于开始时间。')
    return
  }
  if (durationMinutes > 5 * 60) {
    ElMessage.error('单次预约最长为 5 小时。')
    return
  }
  if (props.date < today || props.date > lastBookableDate) {
    ElMessage.error('预约日期必须在今天起连续 14 天内。')
    return
  }
  if (selectedRoom.value?.status !== 'ENABLED') {
    ElMessage.error('该会议室已停用，请选择其他会议室。')
    return
  }

  emit('submit', {
    roomId: form.roomId,
    subject,
    startTime: toDateTime(form.startTime),
    endTime: toDateTime(form.endTime),
    attendeeCount: form.attendeeCount ?? null,
    participantsText: form.participantsText.trim() || null,
    description: form.description.trim() || null,
  })
}
</script>

<template>
  <el-form label-position="top" @submit.prevent="submit">
    <el-form-item label="会议室" required>
      <el-select v-model="form.roomId" :disabled="submitting" aria-label="会议室">
        <el-option v-for="room in rooms" :key="room.id" :label="`${room.name}${room.status === 'DISABLED' ? '（已停用）' : ''}`" :value="room.id" :disabled="room.status === 'DISABLED'" />
      </el-select>
    </el-form-item>
    <el-form-item label="日期"><el-input :model-value="date" disabled /></el-form-item>
    <div class="time-fields">
      <el-form-item label="开始时间" required>
        <el-select v-model="form.startTime" :disabled="submitting" aria-label="开始时间"><el-option v-for="time in timeOptions" :key="time" :label="time" :value="time" /></el-select>
      </el-form-item>
      <el-form-item label="结束时间" required>
        <el-select v-model="form.endTime" :disabled="submitting" aria-label="结束时间"><el-option v-for="time in timeOptions" :key="time" :label="time" :value="time" /></el-select>
      </el-form-item>
    </div>
    <el-form-item label="会议主题" required><el-input v-model="form.subject" :maxlength="200" :disabled="submitting" /></el-form-item>
    <el-form-item label="预计人数"><el-input-number v-model="form.attendeeCount" :min="0" :max="65535" :disabled="submitting" controls-position="right" /></el-form-item>
    <el-alert v-if="isCapacityExceeded" title="预计人数超过会议室容量，请确认。" type="warning" :closable="false" show-icon />
    <el-form-item label="参会人员"><el-input v-model="form.participantsText" type="textarea" :maxlength="2000" :autosize="{ minRows: 2, maxRows: 4 }" :disabled="submitting" /></el-form-item>
    <el-form-item label="说明"><el-input v-model="form.description" type="textarea" :maxlength="4000" :autosize="{ minRows: 2, maxRows: 4 }" :disabled="submitting" /></el-form-item>
    <div class="form-actions">
      <el-button :disabled="submitting" @click="emit('cancel')">取消</el-button>
      <el-button type="primary" native-type="submit" :loading="submitting">确认预约</el-button>
    </div>
  </el-form>
</template>

<style scoped>
.time-fields { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.form-actions { display: flex; justify-content: flex-end; gap: 12px; }
</style>
