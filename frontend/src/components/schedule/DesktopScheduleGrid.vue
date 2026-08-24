<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'

import type { ScheduleBooking, ScheduleResponse } from '@/types/schedule'
import { buildDaySlots, timeToMinutes } from '@/utils/schedule'

import ScheduleRoomRow from './ScheduleRoomRow.vue'
import ScheduleTimeHeader from './ScheduleTimeHeader.vue'

const props = defineProps<{ schedule: ScheduleResponse }>()
defineEmits<{ selectBooking: [booking: ScheduleBooking] }>()

const scrollContainer = ref<HTMLElement>()
const slots = computed(() => buildDaySlots(props.schedule.slotMinutes))
const bookingsByRoomId = computed(() => groupByRoomId(props.schedule.bookings))
const unavailableSlotsByRoomId = computed(() => groupByRoomId(props.schedule.unavailableSlots))
const gridStyle = computed(() => ({ '--slot-count': slots.value.length, '--room-column-width': '230px', '--slot-width': '76px' }))

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
      scrollContainer.value.scrollLeft = Math.max(0, timeToMinutes(props.schedule.focusWindow.start) / props.schedule.slotMinutes * 76 - 230)
    }
  })
}

onMounted(scrollToFocusWindow)
watch(() => props.schedule.date, scrollToFocusWindow)
</script>

<template>
  <el-empty v-if="schedule.rooms.length === 0" description="暂无会议室" />
  <div v-else ref="scrollContainer" class="schedule-grid-scroll">
    <div class="schedule-grid" :style="gridStyle">
      <div class="sticky-time-header">
        <ScheduleTimeHeader :slots="slots" />
      </div>
      <ScheduleRoomRow
        v-for="room in schedule.rooms"
        :key="room.id"
        :room="room"
        :slots="slots"
        :slot-minutes="schedule.slotMinutes"
        :focus-window="schedule.focusWindow"
        :bookings="bookingsByRoomId.get(room.id) ?? []"
        :unavailable-slots="unavailableSlotsByRoomId.get(room.id) ?? []"
        @select-booking="$emit('selectBooking', $event)"
      />
    </div>
  </div>
</template>

<style scoped>
.schedule-grid-scroll { max-height: calc(100vh - 235px); overflow: auto; border: 1px solid #cbd5e1; border-radius: 8px; background: #fff; }
.schedule-grid { min-width: max-content; }
.sticky-time-header { position: sticky; top: 0; z-index: 3; background: #f8fafc; }
</style>
