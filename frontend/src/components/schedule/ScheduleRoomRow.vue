<script setup lang="ts">
import type { ScheduleBooking, ScheduleRoom, UnavailableSlot } from '@/types/schedule'
import type { DaySlot } from '@/utils/schedule'
import { calculateSlotSpan, isInFocusWindow, timeToSlotIndex } from '@/utils/schedule'

import ReservationBlock from './ReservationBlock.vue'
import UnavailableSlotBlock from './UnavailableSlot.vue'

const props = defineProps<{
  room: ScheduleRoom
  slots: DaySlot[]
  slotMinutes: number
  focusWindow: { start: string; end: string }
  bookings: ScheduleBooking[]
  unavailableSlots: UnavailableSlot[]
}>()
defineEmits<{ selectBooking: [booking: ScheduleBooking] }>()

function gridStyle(start: number, span = 1) {
  return { gridColumn: `${start + 1} / span ${span}` }
}
</script>

<template>
  <div class="room-row">
    <div class="room-cell">
      <strong>{{ room.name }}</strong>
      <span>{{ room.capacity }} 人</span>
      <el-tag v-if="room.status === 'DISABLED'" size="small" type="info">已停用</el-tag>
    </div>
    <div class="room-timeline">
      <div class="slot-grid">
        <div
          v-for="slot in slots"
          :key="slot.index"
          class="slot-cell"
          :class="{ 'outside-focus': !isInFocusWindow(slot, focusWindow.start, focusWindow.end), 'hour-boundary': slot.isHour }"
        />
      </div>
      <div class="overlay-grid">
        <div
          v-for="slot in unavailableSlots"
          :key="`${room.id}-${slot.slotStart}`"
          class="timeline-overlay"
          :style="gridStyle(timeToSlotIndex(slot.slotStart, slotMinutes))"
        >
          <UnavailableSlotBlock />
        </div>
        <div
          v-for="booking in bookings"
          :key="booking.id"
          class="timeline-overlay"
          :style="gridStyle(timeToSlotIndex(booking.startTime, slotMinutes), calculateSlotSpan(booking.startTime, booking.endTime, slotMinutes))"
        >
          <ReservationBlock
            :booking="booking"
            :slot-span="calculateSlotSpan(booking.startTime, booking.endTime, slotMinutes)"
            @select="$emit('selectBooking', booking)"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.room-row { display: grid; grid-template-columns: var(--room-column-width) repeat(var(--slot-count), var(--slot-width)); min-width: max-content; min-height: 94px; }
.room-cell { position: sticky; left: 0; z-index: 2; display: flex; flex-direction: column; justify-content: center; gap: 5px; padding: 12px 14px; background: #fff; border-right: 1px solid #cbd5e1; border-bottom: 1px solid #b8c2cf; }
.room-cell strong { color: #1e293b; }
.room-cell span { color: #64748b; font-size: 13px; }
.room-timeline { grid-column: 2 / -1; position: relative; min-height: 94px; border-bottom: 1px solid #b8c2cf; }
.slot-grid, .overlay-grid { display: grid; grid-template-columns: repeat(var(--slot-count), var(--slot-width)); grid-template-rows: minmax(94px, auto); }
.slot-grid { min-height: 94px; }
.overlay-grid { position: absolute; inset: 0; pointer-events: none; }
.slot-cell { grid-row: 1; border-left: 1px solid #cbd5e1; background: #fff; }
.slot-cell.outside-focus { background: #f8fafc; }
.slot-cell.hour-boundary { border-left: 2px solid #64748b; }
.timeline-overlay { z-index: 1; grid-row: 1; min-width: 0; align-self: stretch; margin: 7px 0; pointer-events: auto; }
</style>
