<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'

import { logout } from '@/api/auth'
import ChangePinDialog from '@/components/common/ChangePinDialog.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import { currentUserQueryOptions } from '@/queries/currentUser'

const currentUserQuery = useQuery(currentUserQueryOptions())
const queryClient = useQueryClient()
const isAdmin = computed(() => currentUserQuery.data.value?.roleCode === 'ADMIN')
const route = useRoute()
const router = useRouter()
const isAdminRoute = computed(() => route.path.startsWith('/admin/'))
const isChangePinDialogVisible = ref(false)

async function signOut() {
  try {
    await logout()
    ElMessage.success('已退出登录')
  } finally {
    queryClient.removeQueries({ queryKey: ['current-user'] })
    await router.replace('/login')
  }
}

async function handleUserMenu(command: string) {
  if (command === 'change-pin') {
    isChangePinDialogVisible.value = true
    return
  }
  await signOut()
}

async function handlePinChanged() {
  queryClient.removeQueries({ queryKey: ['current-user'] })
  await router.replace('/login')
}
</script>

<template>
  <header class="app-header">
    <RouterLink class="brand" to="/schedule">中山大学校史馆 · 会议室预约</RouterLink>
    <nav class="main-nav" aria-label="主导航">
      <RouterLink to="/schedule">日程</RouterLink>
      <RouterLink to="/my-reservations">我的预约</RouterLink>
      <el-dropdown v-if="isAdmin">
        <span class="admin-menu" :class="{ 'is-active': isAdminRoute }">管理</span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item
              ><RouterLink to="/admin/reservations">预约管理</RouterLink></el-dropdown-item
            >
            <el-dropdown-item
              ><RouterLink to="/admin/rooms">会议室管理</RouterLink></el-dropdown-item
            >
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </nav>
    <el-dropdown v-if="currentUserQuery.data.value" trigger="click" @command="handleUserMenu">
      <span class="user-summary user-menu">
        <span>{{ currentUserQuery.data.value.displayName }}</span>
        <el-tag v-if="isAdmin" size="small" type="danger">管理员</el-tag>
        <span aria-hidden="true">▼</span>
      </span>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="change-pin">修改密码</el-dropdown-item>
          <el-dropdown-item command="logout">退出登录</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
    <span v-else-if="currentUserQuery.isPending.value">身份加载中...</span>
  </header>
  <ErrorState
    v-if="currentUserQuery.isError.value"
    class="header-error"
    title="当前用户加载失败"
    :error="currentUserQuery.error.value"
    @retry="currentUserQuery.refetch()"
  />
  <ChangePinDialog v-model="isChangePinDialogVisible" @changed="handlePinChanged" />
</template>
