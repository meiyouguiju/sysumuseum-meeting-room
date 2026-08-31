<script setup lang="ts">
import { computed } from 'vue'
import { ElMessage } from 'element-plus'

import type { BookingDetail, CreateBookingResponse } from '@/types/booking'
import { buildWeChatNotification, copyText } from '@/utils/wechatNotification'

const props = defineProps<{
  modelValue: boolean
  booking?: CreateBookingResponse | BookingDetail
}>()
const emit = defineEmits<{ 'update:modelValue': [visible: boolean] }>()
const message = computed(() => (props.booking ? buildWeChatNotification(props.booking) : ''))

async function copy() {
  try {
    await copyText(message.value)
    ElMessage.success('已复制，可直接粘贴到微信群')
  } catch {
    ElMessage.error('复制失败，请手动选择并复制通知内容。')
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="预约成功"
    width="min(520px, calc(100% - 32px))"
    @close="emit('update:modelValue', false)"
  >
    <p class="notification-text">{{ message }}</p>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
      <el-button type="primary" @click="copy">一键复制</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.notification-text {
  white-space: pre-wrap;
  line-height: 1.7;
  user-select: text;
}
</style>
