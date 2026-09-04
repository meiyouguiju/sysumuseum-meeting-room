<script setup lang="ts">
import { computed } from 'vue'

import type { ScheduleBooking } from '@/types/schedule'
import { formatTimeRange, scheduleBookingStatusText } from '@/utils/schedule'

const props = defineProps<{
  booking: ScheduleBooking
  slotSpan: number
  showSlotDividers: boolean
}>()
defineEmits<{ select: [booking: ScheduleBooking] }>()

const statusText = computed(() => scheduleBookingStatusText(props.booking.displayStatus))
const timeRange = computed(() => formatTimeRange(props.booking.startTime, props.booking.endTime))
const dividerIndexes = computed(() =>
  props.showSlotDividers
    ? Array.from({ length: Math.max(0, props.slotSpan - 1) }, (_, index) => index + 1)
    : [],
)
</script>

<template>
  <el-tooltip
    :content="`${booking.subject}｜${booking.organizerName}｜${timeRange}｜${statusText}`"
    placement="top"
  >
    <button
      class="reservation-block"
      :class="[
        `schedule-booking--${booking.displayStatus.toLowerCase()}`,
        { 'reservation-mine': booking.isMine },
      ]"
      @click="$emit('select', booking)"
    >
      <span
        v-for="dividerIndex in dividerIndexes"
        :key="dividerIndex"
        class="reservation-divider"
        :style="{ left: `${(dividerIndex / slotSpan) * 100}%` }"
        aria-hidden="true"
      />
      <span class="reservation-content">
        <strong>{{ booking.subject }}</strong>
        <span v-if="booking.isMine" class="mine-label">我的预约</span>
        <span class="reservation-organizer">{{ booking.organizerName }}</span>
        <span>{{ timeRange }}</span>
        <span class="reservation-status">{{ statusText }}</span>
      </span>
    </button>
  </el-tooltip>
</template>

<style scoped>
.reservation-block {
  position: relative;
  width: 100%;
  height: 100%;
  min-width: 0;
  border: 0;
  border-radius: 4px;
  padding: 5px 7px;
  text-align: left;
  overflow: hidden;
  cursor: pointer;
  font-size: 12px;
  line-height: 1.25;
  outline: 1px solid rgba(51, 65, 85, 0.45);
  outline-offset: -1px;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.16);
}
.reservation-content {
  position: relative;
  z-index: 1;
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 1px;
}
.reservation-content strong,
.reservation-content span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.reservation-content strong {
  font-size: 13px;
}
.reservation-divider {
  position: absolute;
  top: 0;
  bottom: 0;
  z-index: 0;
  width: 1px;
  background: rgb(30 64 175 / 42%);
  pointer-events: none;
}
.reservation-status {
  font-weight: 700;
}
.reservation-mine {
  outline: 2px solid #047857;
  outline-offset: -2px;
}
.mine-label {
  width: fit-content;
  padding: 1px 4px;
  border-radius: 3px;
  color: #fff;
  background: #047857;
  font-weight: 700;
}
@media (max-width: 760px) {
  .reservation-organizer {
    display: none;
  }
}
</style>
