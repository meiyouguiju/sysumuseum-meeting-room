<script setup lang="ts">
import type { DaySlot } from '@/utils/schedule'

defineProps<{ slots: DaySlot[] }>()
</script>

<template>
  <div class="time-header-grid">
    <div class="corner-cell">会议室</div>
    <div v-for="slot in slots" :key="slot.index" class="time-cell">
      <span v-if="slot.isHour" class="time-label">{{ slot.label }}</span>
    </div>
  </div>
</template>

<style scoped>
.time-header-grid {
  display: grid;
  grid-template-columns: var(--room-column-width) repeat(var(--slot-count), var(--slot-width));
  min-width: max-content;
}
.corner-cell {
  position: sticky;
  left: 0;
  z-index: 4;
  display: flex;
  align-items: center;
  padding: 0 14px;
  background: #f8fafc;
  border-bottom: 1px solid #cbd5e1;
  color: #334155;
  font-weight: 700;
}
.time-cell {
  position: relative;
  height: 42px;
  overflow: visible;
  border-bottom: 1px solid #cbd5e1;
}
.time-label {
  position: absolute;
  top: 13px;
  left: 0;
  transform: translateX(-50%);
  color: #334155;
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
  white-space: nowrap;
}
</style>
