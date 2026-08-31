<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { login } from '@/api/auth'
import { queryClient } from '@/router'
import { currentUserQueryKey } from '@/queries/currentUser'
import { ApiError } from '@/types/api'

const router = useRouter()
const route = useRoute()
const name = ref('')
const pin = ref('')
const isSubmitting = ref(false)
const redirectTarget = computed(() =>
  typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
    ? route.query.redirect
    : '/schedule',
)

function normalizePin() {
  pin.value = pin.value.replace(/\D/g, '').slice(0, 4)
}

async function submit() {
  if (!name.value.trim() || !/^\d{4}$/.test(pin.value)) {
    ElMessage.warning('请输入姓名和4位数字 PIN。')
    return
  }

  isSubmitting.value = true
  try {
    const currentUser = await login({ name: name.value.trim(), pin: pin.value })
    queryClient.setQueryData(currentUserQueryKey, currentUser)
    await router.replace(redirectTarget.value)
  } catch (error) {
    const apiError = error as ApiError
    ElMessage.error(apiError.message ?? '姓名或 PIN 不正确。')
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <el-card class="login-card" shadow="never">
      <h1>会议室预约系统</h1>
      <p>中山大学博物馆（校史馆）</p>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="姓名">
          <el-input v-model="name" maxlength="100" autocomplete="name" />
        </el-form-item>
        <el-form-item label="PIN">
          <el-input
            v-model="pin"
            type="password"
            maxlength="4"
            inputmode="numeric"
            autocomplete="current-password"
            @input="normalizePin"
          />
        </el-form-item>
        <el-button class="login-button" type="primary" native-type="submit" :loading="isSubmitting"
          >登录</el-button
        >
      </el-form>
    </el-card>
  </main>
</template>

<style scoped>
.login-page {
  display: grid;
  min-height: 100vh;
  padding: 24px;
  place-items: center;
  background: #f1f5f9;
}
.login-card {
  width: min(100%, 380px);
}
h1 {
  margin: 0;
  color: #14532d;
  font-size: 28px;
}
p {
  margin: 8px 0 28px;
  color: #64748b;
}
.login-button {
  width: 100%;
}
</style>
