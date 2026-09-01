<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'

import { logout } from '@/api/auth'
import ErrorState from '@/components/common/ErrorState.vue'
import { currentUserQueryOptions } from '@/queries/currentUser'

const currentUserQuery = useQuery(currentUserQueryOptions())
const queryClient = useQueryClient()
const isAdmin = computed(() => currentUserQuery.data.value?.roleCode === 'ADMIN')
const route = useRoute()
const router = useRouter()
const isAdminRoute = computed(() => route.path.startsWith('/admin/'))

async function signOut() {
  try {
    await logout()
    ElMessage.success('已退出登录')
  } finally {
    queryClient.removeQueries({ queryKey: ['current-user'] })
    await router.replace('/login')
  }
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
    <div v-if="currentUserQuery.data.value" class="user-summary">
      <span>{{ currentUserQuery.data.value.displayName }}</span>
      <el-tag v-if="isAdmin" size="small" type="danger">管理员</el-tag>
      <el-button link type="primary" @click="signOut">退出登录</el-button>
    </div>
    <span v-else-if="currentUserQuery.isPending.value">身份加载中...</span>
  </header>
  <ErrorState
    v-if="currentUserQuery.isError.value"
    class="header-error"
    title="当前用户加载失败"
    :error="currentUserQuery.error.value"
    @retry="currentUserQuery.refetch()"
  />
</template>
