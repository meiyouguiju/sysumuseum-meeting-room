<script setup lang="ts">
import { computed, reactive } from 'vue'
import { ElMessage } from 'element-plus'

import type {
  AdminInProgressBookingUpdateRequest,
  AdminUpcomingBookingUpdateRequest,
} from '@/types/admin'
import type { BookingDetail } from '@/types/booking'
import type { Room } from '@/types/room'
import { shiftDate, todayInShanghai } from '@/utils/schedule'

const props = defineProps<{
  booking: BookingDetail
  isOwnBooking: boolean
  rooms: Room[]
  submitting: boolean
}>()

const emit = defineEmits<{
  cancel: []
  submit: [request: AdminUpcomingBookingUpdateRequest | AdminInProgressBookingUpdateRequest]
}>()

const isInProgress = computed(() => props.booking.displayStatus === 'IN_PROGRESS')
const isReasonRequired = computed(() => !props.isOwnBooking)
const timeOptions = Array.from({ length: 48 }, (_, index) => {
  const minutes = index * 30
  return `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`
})
const form = reactive({
  date: props.booking.startTime.slice(0, 10),
  roomId: props.booking.room.id,
  startTime: props.booking.startTime.slice(11, 16),
  endTime: props.booking.endTime.slice(11, 16),
  subject: props.booking.subject,
  attendeeCount: props.booking.attendeeCount ?? undefined,
  participantsText: props.booking.participantsText ?? '',
  description: props.booking.description ?? '',
  reason: '',
})
const selectedRoom = computed(() => props.rooms.find((room) => room.id === form.roomId))
const capacityExceeded = computed(
  () =>
    form.attendeeCount !== undefined &&
    form.attendeeCount > (selectedRoom.value?.capacity ?? Infinity),
)

function submit() {
  const subject = form.subject.trim()
  const reason = form.reason.trim() || null
  if (!subject) {
    ElMessage.error('请输入会议主题。')
    return
  }
  if (isReasonRequired.value && !reason) {
    ElMessage.error('修改他人预约必须填写修改原因。')
    return
  }

  if (isInProgress.value) {
    emit('submit', {
      version: props.booking.version,
      subject,
      attendeeCount: form.attendeeCount ?? null,
      participantsText: form.participantsText.trim() || null,
      description: form.description.trim() || null,
      reason,
    })
    return
  }

  const startIndex = timeOptions.indexOf(form.startTime)
  const endIndex = timeOptions.indexOf(form.endTime)
  if (endIndex <= startIndex || (endIndex - startIndex) * 30 > 300) {
    ElMessage.error('请选择 30 分钟至 5 小时的预约时段。')
    return
  }
  if (form.date < todayInShanghai() || form.date > shiftDate(todayInShanghai(), 13)) {
    ElMessage.error('预约日期必须在今天起连续 14 天内。')
    return
  }
  if (selectedRoom.value?.status !== 'ENABLED') {
    ElMessage.error('该会议室已停用，请选择其他会议室。')
    return
  }
  emit('submit', {
    version: props.booking.version,
    roomId: form.roomId,
    subject,
    startTime: `${form.date}T${form.startTime}:00`,
    endTime: `${form.date}T${form.endTime}:00`,
    attendeeCount: form.attendeeCount ?? null,
    participantsText: form.participantsText.trim() || null,
    description: form.description.trim() || null,
    reason,
  })
}
</script>

<template>
  <el-form label-position="top" @submit.prevent="submit">
    <template v-if="isInProgress">
      <el-alert
        title="预约已开始，会议室和时间不可修改。"
        type="info"
        :closable="false"
        show-icon
      />
      <el-descriptions :column="1" border>
        <el-descriptions-item label="会议室">{{ booking.room.name }}</el-descriptions-item>
        <el-descriptions-item label="日期与时间">
          {{ booking.startTime.slice(0, 10) }} {{ booking.startTime.slice(11, 16) }}–{{
            booking.endTime.slice(11, 16)
          }}
        </el-descriptions-item>
      </el-descriptions>
    </template>
    <template v-else>
      <el-form-item label="会议室" required
        ><el-select v-model="form.roomId" :disabled="submitting"
          ><el-option
            v-for="room in rooms"
            :key="room.id"
            :label="`${room.name}${room.status === 'DISABLED' ? '（已停用）' : ''}`"
            :value="room.id"
            :disabled="room.status === 'DISABLED'" /></el-select
      ></el-form-item>
      <el-form-item label="日期" required
        ><el-date-picker
          v-model="form.date"
          type="date"
          value-format="YYYY-MM-DD"
          :disabled="submitting"
      /></el-form-item>
      <div class="time-fields">
        <el-form-item label="开始时间" required
          ><el-select v-model="form.startTime" :disabled="submitting"
            ><el-option
              v-for="time in timeOptions"
              :key="time"
              :label="time"
              :value="time" /></el-select></el-form-item
        ><el-form-item label="结束时间" required
          ><el-select v-model="form.endTime" :disabled="submitting"
            ><el-option
              v-for="time in timeOptions"
              :key="time"
              :label="time"
              :value="time" /></el-select
        ></el-form-item>
      </div>
    </template>
    <el-form-item label="会议主题" required
      ><el-input v-model="form.subject" :maxlength="200" :disabled="submitting"
    /></el-form-item>
    <el-form-item label="预计人数"
      ><el-input-number
        v-model="form.attendeeCount"
        :min="0"
        :max="65535"
        :disabled="submitting"
        controls-position="right"
    /></el-form-item>
    <el-alert
      v-if="capacityExceeded"
      title="预计人数超过会议室容量，请确认。"
      type="warning"
      :closable="false"
      show-icon
    />
    <el-form-item label="参会人员"
      ><el-input
        v-model="form.participantsText"
        type="textarea"
        :maxlength="2000"
        :disabled="submitting"
    /></el-form-item>
    <el-form-item label="说明"
      ><el-input
        v-model="form.description"
        type="textarea"
        :maxlength="4000"
        :disabled="submitting"
    /></el-form-item>
    <el-form-item
      :label="isReasonRequired ? '修改原因（必填）' : '修改原因'"
      :required="isReasonRequired"
      ><el-input v-model="form.reason" type="textarea" :maxlength="500" :disabled="submitting"
    /></el-form-item>
    <div class="actions">
      <el-button :disabled="submitting" @click="emit('cancel')">取消</el-button
      ><el-button type="primary" native-type="submit" :loading="submitting">确认修改</el-button>
    </div>
  </el-form>
</template>

<style scoped>
.time-fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
