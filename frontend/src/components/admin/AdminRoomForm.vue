<script setup lang="ts">
import { reactive } from 'vue'
import { ElMessage } from 'element-plus'

import type { CreateRoomRequest } from '@/types/admin'
import type { Room } from '@/types/room'

const props = defineProps<{ room?: Room; submitting: boolean }>()
const emit = defineEmits<{ cancel: []; submit: [request: CreateRoomRequest] }>()
const form = reactive({
  name: props.room?.name ?? '',
  location: props.room?.location ?? '',
  capacity: props.room?.capacity ?? undefined,
  facilitiesText: props.room?.facilitiesText ?? '',
  usageNotice: props.room?.usageNotice ?? '',
  sortOrder: props.room?.sortOrder ?? undefined,
})
function submit() {
  if (!form.name.trim() || !form.location.trim() || !form.capacity) {
    ElMessage.error('请填写会议室名称、位置和容量。')
    return
  }
  emit('submit', {
    name: form.name.trim(),
    location: form.location.trim(),
    capacity: form.capacity,
    facilitiesText: form.facilitiesText.trim() || null,
    usageNotice: form.usageNotice.trim() || null,
    sortOrder: form.sortOrder ?? null,
  })
}
</script>
<template>
  <el-form label-position="top" @submit.prevent="submit"
    ><el-form-item label="会议室名称" required
      ><el-input v-model="form.name" :maxlength="120" :disabled="submitting" /></el-form-item
    ><el-form-item label="位置" required
      ><el-input v-model="form.location" :maxlength="200" :disabled="submitting" /></el-form-item
    ><el-form-item label="容量" required
      ><el-input-number
        v-model="form.capacity"
        :min="1"
        :max="65535"
        :disabled="submitting" /></el-form-item
    ><el-form-item label="设施说明"
      ><el-input
        v-model="form.facilitiesText"
        type="textarea"
        :disabled="submitting" /></el-form-item
    ><el-form-item label="使用须知"
      ><el-input v-model="form.usageNotice" type="textarea" :disabled="submitting" /></el-form-item
    ><el-form-item label="排序"
      ><el-input-number v-model="form.sortOrder" :disabled="submitting"
    /></el-form-item>
    <div class="actions">
      <el-button :disabled="submitting" @click="emit('cancel')">取消</el-button
      ><el-button type="primary" native-type="submit" :loading="submitting">保存</el-button>
    </div></el-form
  >
</template>
<style scoped>
.actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
