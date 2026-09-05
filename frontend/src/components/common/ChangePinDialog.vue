<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

import { changePin } from '@/api/auth'
import { ApiError } from '@/types/api'

const visible = defineModel<boolean>({ default: false })
const emit = defineEmits<{ changed: [] }>()

const currentPin = ref('')
const newPin = ref('')
const confirmNewPin = ref('')
const isSubmitting = ref(false)

function normalizePin(pin: string) {
  return pin.replace(/\D/g, '').slice(0, 4)
}

function resetForm() {
  currentPin.value = ''
  newPin.value = ''
  confirmNewPin.value = ''
}

async function submit() {
  if (![currentPin.value, newPin.value, confirmNewPin.value].every((pin) => /^\d{4}$/.test(pin))) {
    ElMessage.warning('请输入三个4位数字 PIN。')
    return
  }
  if (newPin.value !== confirmNewPin.value) {
    ElMessage.warning('两次输入的新 PIN 不一致。')
    return
  }
  if (newPin.value === currentPin.value) {
    ElMessage.warning('新 PIN 不能与当前 PIN 相同。')
    return
  }

  isSubmitting.value = true
  try {
    await changePin({ currentPin: currentPin.value, newPin: newPin.value })
    ElMessage.success('密码修改成功，请使用新 PIN 重新登录。')
    visible.value = false
    emit('changed')
  } catch (error) {
    const apiError = error as ApiError
    ElMessage.error(apiError.message ?? '密码修改失败，请稍后重试。')
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="修改密码" width="min(92vw, 420px)" @closed="resetForm">
    <el-form label-position="top" @submit.prevent="submit">
      <el-form-item label="当前 PIN">
        <el-input
          v-model="currentPin"
          type="password"
          maxlength="4"
          inputmode="numeric"
          autocomplete="current-password"
          @input="currentPin = normalizePin(currentPin)"
        />
      </el-form-item>
      <el-form-item label="新 PIN">
        <el-input
          v-model="newPin"
          type="password"
          maxlength="4"
          inputmode="numeric"
          autocomplete="new-password"
          @input="newPin = normalizePin(newPin)"
        />
      </el-form-item>
      <el-form-item label="确认新 PIN">
        <el-input
          v-model="confirmNewPin"
          type="password"
          maxlength="4"
          inputmode="numeric"
          autocomplete="new-password"
          @input="confirmNewPin = normalizePin(confirmNewPin)"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="isSubmitting" @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="isSubmitting" @click="submit">确认修改</el-button>
    </template>
  </el-dialog>
</template>
