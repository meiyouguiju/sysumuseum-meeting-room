<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import type { ScheduleBooking, ScheduleResponse, ScheduleRoom } from '@/types/schedule'
import type { DaySlot } from '@/utils/schedule'
import {
  buildDaySlots,
  currentMinutesInShanghai,
  timeToMinutes,
  todayInShanghai,
} from '@/utils/schedule'

import ScheduleRoomRow from './ScheduleRoomRow.vue'
import ScheduleTimeHeader from './ScheduleTimeHeader.vue'

const props = defineProps<{ schedule: ScheduleResponse }>()
const emit = defineEmits<{
  selectBooking: [booking: ScheduleBooking]
  selectEmptySlot: [room: ScheduleRoom, slot: DaySlot]
}>()

const scrollContainer = ref<HTMLElement>()
const ROOM_COLUMN_WIDTH = 230
const SLOT_WIDTH = 76
const slots = computed(() => buildDaySlots(props.schedule.slotMinutes))
const bookingsByRoomId = computed(() => groupByRoomId(props.schedule.bookings))
const unavailableSlotsByRoomId = computed(() => groupByRoomId(props.schedule.unavailableSlots))
const gridStyle = computed(() => ({
  '--slot-count': slots.value.length,
  '--room-column-width': `${ROOM_COLUMN_WIDTH}px`,
  '--slot-width': `${SLOT_WIDTH}px`,
}))
const currentMinutes = ref(currentMinutesInShanghai())
const showCurrentTimeLine = computed(
  () => currentMinutes.value >= 0 && props.schedule.date === todayInShanghai(),
)
const currentTimeLineStyle = computed(() => ({
  left: `${ROOM_COLUMN_WIDTH + (currentMinutes.value / props.schedule.slotMinutes) * SLOT_WIDTH}px`,
}))
const currentTimeLabel = computed(
  () =>
    `${String(Math.floor(currentMinutes.value / 60)).padStart(2, '0')}:${String(currentMinutes.value % 60).padStart(2, '0')}`,
)
let currentTimeTimer: number | undefined

function groupByRoomId<T extends { roomId: number }>(items: T[]): Map<number, T[]> {
  return items.reduce((grouped, item) => {
    const currentItems = grouped.get(item.roomId) ?? []
    currentItems.push(item)
    grouped.set(item.roomId, currentItems)
    return grouped
  }, new Map<number, T[]>())
}

function scrollToFocusWindow() {
  nextTick(() => {
    if (scrollContainer.value) {
      scrollContainer.value.scrollLeft = Math.max(
        0,
        (timeToMinutes(props.schedule.focusWindow.start) / props.schedule.slotMinutes) * 76 - 230,
      )
    }
  })
}

function handleEmptySlot(room: ScheduleRoom, slot: DaySlot) {
  emit('selectEmptySlot', room, slot)
}

function updateCurrentTimeLine() {
  currentMinutes.value = currentMinutesInShanghai()
}

function stopCurrentTimeTimer() {
  if (currentTimeTimer !== undefined) {
    window.clearInterval(currentTimeTimer)
    currentTimeTimer = undefined
  }
}

function syncCurrentTimeTimer() {
  stopCurrentTimeTimer()
  if (showCurrentTimeLine.value) {
    updateCurrentTimeLine()
    currentTimeTimer = window.setInterval(updateCurrentTimeLine, 60_000)
  }
}

onMounted(() => {
  scrollToFocusWindow()
  syncCurrentTimeTimer()
})
watch(
  () => props.schedule.date,
  () => {
    scrollToFocusWindow()
    syncCurrentTimeTimer()
  },
)
onBeforeUnmount(stopCurrentTimeTimer)
</script>

<template>
  <el-empty v-if="schedule.rooms.length === 0" description="暂无会议室" />
  <div v-else ref="scrollContainer" class="schedule-grid-scroll">
    <div class="schedule-grid" :style="gridStyle">
      <div class="sticky-time-header">
        <ScheduleTimeHeader :slots="slots" />
      </div>
      <div
        v-if="showCurrentTimeLine"
        class="current-time-label"
        :style="currentTimeLineStyle"
        aria-hidden="true"
      >
        {{ currentTimeLabel }}
      </div>
      <div
        v-if="showCurrentTimeLine"
        class="current-time-line"
        :style="currentTimeLineStyle"
        aria-hidden="true"
      />
      <ScheduleRoomRow
        v-for="room in schedule.rooms"
        :key="room.id"
        :room="room"
        :slots="slots"
        :slot-minutes="schedule.slotMinutes"
        :date="schedule.date"
        :focus-window="schedule.focusWindow"
        :bookings="bookingsByRoomId.get(room.id) ?? []"
        :unavailable-slots="unavailableSlotsByRoomId.get(room.id) ?? []"
        @select-booking="$emit('selectBooking', $event)"
        @select-empty-slot="handleEmptySlot"
      />
    </div>
  </div>
</template>

<style scoped>
.schedule-grid-scroll {
  max-height: calc(100vh - 235px);
  overflow: auto;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #fff;
}
.schedule-grid {
  position: relative;
  min-width: max-content;
}
.sticky-time-header {
  position: sticky;
  top: 0;
  z-index: 3;
  background: #f8fafc;
}
.current-time-label {
  position: absolute;
  top: 3px;
  z-index: 4;
  transform: translateX(-50%);
  padding: 2px 5px;
  border-radius: 3px;
  color: #fff;
  background: #ef4444;
  font-size: 11px;
  font-weight: 700;
  line-height: 1.2;
  pointer-events: none;
}
.current-time-line {
  position: absolute;
  top: 42px;
  bottom: 0;
  z-index: 2;
  border-left: 2px dashed #ef4444;
  pointer-events: none;
}
</style>
