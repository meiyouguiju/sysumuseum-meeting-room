<script setup lang="ts">
import { reactive } from 'vue'

import type { BookingDetail, SupplementalInfoRequest } from '@/types/booking'

const props = defineProps<{ booking: BookingDetail; submitting: boolean }>()
const emit = defineEmits<{
  cancel: []
  submit: [request: Omit<SupplementalInfoRequest, 'version'>]
}>()

const form = reactive({
  attendeeCount: props.booking.attendeeCount,
  participantsText: props.booking.participantsText ?? '',
  description: props.booking.description ?? '',
})

function normalizeOptionalText(value: string) {
  const normalized = value.trim()
  return normalized || null
}

function submit() {
  emit('submit', {
    attendeeCount: form.attendeeCount ?? null,
    participantsText: normalizeOptionalText(form.participantsText),
    description: normalizeOptionalText(form.description),
  })
}
</script>

<template>
  <el-form label-position="top" @submit.prevent="submit">
    <el-form-item label="预计人数">
      <el-input-number
        v-model="form.attendeeCount"
        :min="0"
        :max="65535"
        controls-position="right"
        class="full-width"
      />
    </el-form-item>
    <el-form-item label="参会人员">
      <el-input
        v-model="form.participantsText"
        type="textarea"
        :rows="4"
        maxlength="2000"
        show-word-limit
      />
    </el-form-item>
    <el-form-item label="说明">
      <el-input
        v-model="form.description"
        type="textarea"
        :rows="5"
        maxlength="4000"
        show-word-limit
        placeholder="可填写补充说明、会议记录或会后备注"
      />
    </el-form-item>
    <div class="actions">
      <el-button :disabled="submitting" @click="emit('cancel')">取消</el-button>
      <el-button type="primary" native-type="submit" :loading="submitting">保存补充信息</el-button>
    </div>
  </el-form>
</template>

<style scoped>
.full-width {
  width: 100%;
}
.actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
