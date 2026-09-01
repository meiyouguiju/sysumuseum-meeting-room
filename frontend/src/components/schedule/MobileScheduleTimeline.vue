<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import type { ScheduleBooking, ScheduleResponse, ScheduleRoom } from '@/types/schedule'
import type { DaySlot } from '@/utils/schedule'
import {
  buildDaySlots,
  calculateSlotSpan,
  currentMinutesInShanghai,
  getScheduleSlotState,
  timeToSlotIndex,
  todayInShanghai,
  timeToMinutes,
} from '@/utils/schedule'

const props = defineProps<{ schedule: ScheduleResponse; room: ScheduleRoom }>()
const emit = defineEmits<{
  selectBooking: [booking: ScheduleBooking]
  selectEmptySlot: [room: ScheduleRoom, slot: DaySlot]
}>()
const timeline = ref<HTMLElement>()
const slots = computed(() => buildDaySlots(props.schedule.slotMinutes))
const bookings = computed(() =>
  props.schedule.bookings.filter((booking) => booking.roomId === props.room.id),
)
const unavailableSlots = computed(() =>
  props.schedule.unavailableSlots.filter((slot) => slot.roomId === props.room.id),
)
const currentMinutes = ref(currentMinutesInShanghai())
const showCurrentTime = computed(() => props.schedule.date === todayInShanghai())
const currentLineStyle = computed(() => ({
  top: `${(currentMinutes.value / props.schedule.slotMinutes) * 56}px`,
}))
let timer: number | undefined

function bookingAt(slot: DaySlot): ScheduleBooking | undefined {
  return bookings.value.find(
    (booking) => timeToSlotIndex(booking.startTime, props.schedule.slotMinutes) === slot.index,
  )
}
function bookingSpan(booking: ScheduleBooking) {
  return calculateSlotSpan(booking.startTime, booking.endTime, props.schedule.slotMinutes)
}
function slotState(slot: DaySlot) {
  return getScheduleSlotState({
    date: props.schedule.date,
    roomStatus: props.room.status,
    slot,
    slots: slots.value,
    slotMinutes: props.schedule.slotMinutes,
    focusWindow: props.schedule.focusWindow,
    bookings: bookings.value,
    unavailableSlots: unavailableSlots.value,
  })
}
function scrollToFocus() {
  nextTick(() => {
    const focusSlot = Math.floor(
      timeToMinutes(props.schedule.focusWindow.start) / props.schedule.slotMinutes,
    )
    timeline.value?.scrollTo({ top: Math.max(0, focusSlot * 56 - 120) })
  })
}
function syncTimer() {
  if (timer) window.clearInterval(timer)
  if (showCurrentTime.value)
    timer = window.setInterval(() => {
      currentMinutes.value = currentMinutesInShanghai()
    }, 60_000)
}
onMounted(() => {
  scrollToFocus()
  syncTimer()
})
watch(
  () => props.schedule.date,
  () => {
    scrollToFocus()
    syncTimer()
  },
)
onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
})
</script>
<template>
  <div ref="timeline" class="mobile-timeline">
    <div
      v-if="showCurrentTime"
      class="mobile-current-time"
      :style="currentLineStyle"
      aria-hidden="true"
    >
      {{ String(Math.floor(currentMinutes / 60)).padStart(2, '0') }}:{{
        String(currentMinutes % 60).padStart(2, '0')
      }}
    </div>
    <div
      v-for="slot in slots"
      :key="slot.index"
      class="schedule-slot mobile-slot"
      :class="{
        'is-focus': slotState(slot).isInFocusWindow,
        'is-outside-focus': !slotState(slot).isInFocusWindow,
        [`is-${slotState(slot).timePosition}`]: true,
        [`is-${slotState(slot).availability}`]: true,
      }"
    >
      <span class="slot-time">{{ slot.label }}</span
      ><button
        v-if="bookingAt(slot)"
        class="mobile-booking"
        :class="[
          `schedule-booking--${bookingAt(slot)!.displayStatus.toLowerCase()}`,
          { 'mobile-booking-mine': bookingAt(slot)?.isMine },
        ]"
        :style="{ '--booking-span': bookingSpan(bookingAt(slot)!) }"
        @click="emit('selectBooking', bookingAt(slot)!)"
      >
        <strong>{{ bookingAt(slot)?.subject }}</strong>
        <span v-if="bookingAt(slot)?.isMine" class="mobile-mine-label">我的预约</span>
      </button>
      <div v-else-if="slotState(slot).availability === 'booking'" class="booking-continuation" />
      <div v-else-if="slotState(slot).availability === 'current-slot-hold'" class="unavailable">
        暂不可预约
      </div>
      <button
        v-else-if="slotState(slot).isBookable"
        class="empty-slot"
        @click="emit('selectEmptySlot', room, slot)"
      >
        预约
      </button>
      <div v-else class="empty-slot disabled" />
    </div>
  </div>
</template>
<style scoped>
.mobile-timeline {
  position: relative;
  max-height: calc(100vh - 260px);
  overflow: auto;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #fff;
}
.mobile-slot {
  position: relative;
  display: grid;
  grid-template-columns: 64px 1fr;
  min-height: 56px;
  border-bottom: 1px solid #cbd5e1;
  background: #fff;
}
.slot-time {
  padding: 8px;
  color: #475569;
  font-size: 13px;
  border-right: 1px solid #cbd5e1;
}
.empty-slot,
.unavailable,
.booking-continuation {
  min-height: 55px;
  border: 0;
  background: transparent;
  text-align: left;
  padding: 8px 12px;
}
.empty-slot {
  color: #047857;
  font: inherit;
  font-weight: 600;
}
.empty-slot.disabled,
.unavailable {
  color: #64748b;
}
.unavailable {
  background: repeating-linear-gradient(-45deg, #e2e8f0, #e2e8f0 4px, #f8fafc 4px, #f8fafc 8px);
}
.mobile-booking {
  position: absolute;
  top: 3px;
  right: 6px;
  left: 70px;
  z-index: 2;
  display: grid;
  align-content: center;
  gap: 2px;
  height: calc(var(--booking-span) * 56px - 6px);
  min-width: 0;
  padding: 8px;
  border: 0;
  border-radius: 6px;
  text-align: left;
  font: inherit;
  cursor: pointer;
}
.mobile-booking strong,
.mobile-booking span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  line-height: 1.2;
}
.mobile-booking strong {
  display: block;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mobile-booking span {
  font-size: 8px;
}
.mobile-booking-mine {
  outline: 2px solid #047857;
  outline-offset: -2px;
}
.mobile-mine-label {
  width: fit-content;
  max-width: 100%;
  padding: 1px 4px;
  border-radius: 3px;
  color: #fff;
  background: #047857;
  font-size: 10px;
  font-weight: 700;
  line-height: 1.2;
  white-space: nowrap;
}
.mobile-current-time {
  position: absolute;
  left: 56px;
  right: 0;
  z-index: 3;
  border-top: 2px dashed #ef4444;
  color: #dc2626;
  font-size: 12px;
  font-weight: 700;
  pointer-events: none;
}
</style>
