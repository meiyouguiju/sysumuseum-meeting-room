<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'

import ErrorState from '@/components/common/ErrorState.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import { currentUserQueryOptions } from '@/queries/currentUser'
import { roomsQueryOptions } from '@/queries/rooms'

const currentUserQuery = useQuery(currentUserQueryOptions())
const roomsQuery = useQuery(roomsQueryOptions())
</script>

<template>
  <section>
    <h1>会议室预约系统</h1>
    <el-alert title="二维日程将在 Frontend F1 实现" type="info" :closable="false" show-icon />

    <div class="card-grid">
      <el-card>
        <template #header><strong>当前用户</strong></template>
        <LoadingState v-if="currentUserQuery.isPending.value" />
        <ErrorState
          v-else-if="currentUserQuery.isError.value"
          :error="currentUserQuery.error.value"
          @retry="currentUserQuery.refetch()"
        />
        <el-descriptions v-else-if="currentUserQuery.data.value" :column="1" border>
          <el-descriptions-item label="姓名">{{ currentUserQuery.data.value.displayName }}</el-descriptions-item>
          <el-descriptions-item label="部门">{{ currentUserQuery.data.value.departmentName }}</el-descriptions-item>
          <el-descriptions-item label="角色">{{ currentUserQuery.data.value.roleCode }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card>
        <template #header><strong>会议室</strong></template>
        <LoadingState v-if="roomsQuery.isPending.value" />
        <ErrorState
          v-else-if="roomsQuery.isError.value"
          :error="roomsQuery.error.value"
          @retry="roomsQuery.refetch()"
        />
        <el-table v-else :data="roomsQuery.data.value ?? []" empty-text="暂无会议室">
          <el-table-column prop="name" label="名称" min-width="180" />
          <el-table-column prop="location" label="位置" min-width="150" />
          <el-table-column prop="capacity" label="容量" width="90" />
          <el-table-column label="状态" width="100">
            <template #default="scope">
              <el-tag :type="scope.row.status === 'ENABLED' ? 'success' : 'info'">
                {{ scope.row.status === 'ENABLED' ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>
  </section>
</template>
