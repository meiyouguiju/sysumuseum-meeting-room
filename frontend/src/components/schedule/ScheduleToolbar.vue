<script setup lang="ts">
import { computed } from 'vue'

import { shiftDate, todayInShanghai } from '@/utils/schedule'

const props = defineProps<{ modelValue: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const today = todayInShanghai()
const lastSelectableDate = shiftDate(today, 13)
const canGoNext = computed(() => props.modelValue < lastSelectableDate)

function changeDate(amount: number) {
  const nextDate = shiftDate(props.modelValue, amount)
  if (nextDate <= lastSelectableDate) {
    emit('update:modelValue', nextDate)
  }
}

function disableDate(date: Date): boolean {
  return date.getTime() > new Date(`${lastSelectableDate}T00:00:00`).getTime()
}

function updateSelectedDate(value: string | null) {
  if (value) {
    emit('update:modelValue', value)
  }
}
</script>

<template>
  <div class="schedule-toolbar">
    <el-button aria-label="前一天" @click="changeDate(-1)">前一天</el-button>
    <el-date-picker
      :model-value="modelValue"
      type="date"
      value-format="YYYY-MM-DD"
      format="YYYY 年 MM 月 DD 日"
      :editable="false"
      :clearable="false"
      :disabled-date="disableDate"
      aria-label="选择日程日期"
      @update:model-value="updateSelectedDate"
    />
    <el-button aria-label="后一天" :disabled="!canGoNext" @click="changeDate(1)">后一天</el-button>
    <el-button plain @click="$emit('update:modelValue', today)">今天</el-button>
  </div>
</template>

<style scoped>
.schedule-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

@media (max-width: 800px) {
  .schedule-toolbar {
    width: 100%;
    flex-wrap: nowrap;
    gap: 6px;
  }

  .schedule-toolbar :deep(.el-date-editor) {
    width: auto;
    min-width: 0;
    flex: 1;
  }

  .schedule-toolbar :deep(.el-button) {
    min-height: 40px;
    padding: 8px;
    white-space: nowrap;
  }
}
</style>
