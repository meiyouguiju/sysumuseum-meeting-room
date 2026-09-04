<script setup lang="ts">
import { ref } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'

import { createRoom, disableRoom, enableRoom, updateRoom } from '@/api/admin/rooms'
import AdminRoomForm from '@/components/admin/AdminRoomForm.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import { roomsQueryKey, roomsQueryOptions } from '@/queries/rooms'
import type { CreateRoomRequest } from '@/types/admin'
import { ApiError } from '@/types/api'
import type { Room } from '@/types/room'

const roomsQuery = useQuery(roomsQueryOptions())
const queryClient = useQueryClient()
const formVisible = ref(false)
const editingRoom = ref<Room>()
const submitting = ref(false)

function openCreate() {
  editingRoom.value = undefined
  formVisible.value = true
}
function openEdit(room: Room) {
  editingRoom.value = room
  formVisible.value = true
}
function closeForm() {
  formVisible.value = false
  editingRoom.value = undefined
}
async function invalidateRooms() {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: roomsQueryKey }),
    queryClient.invalidateQueries({ queryKey: ['schedule'] }),
  ])
}
async function saveRoom(request: CreateRoomRequest) {
  submitting.value = true
  try {
    if (editingRoom.value) {
      await updateRoom(editingRoom.value.id, request)
      ElMessage.success('会议室已修改')
    } else {
      await createRoom(request)
      ElMessage.success('会议室已新增')
    }
    await invalidateRooms()
    closeForm()
  } catch (error) {
    ElMessage.error((error as ApiError).message ?? '保存失败，请稍后重试。')
  } finally {
    submitting.value = false
  }
}
async function toggleRoom(room: Room) {
  const disabling = room.status === 'ENABLED'
  try {
    if (disabling)
      await ElMessageBox.confirm(
        '停用后：会议室仍然可见；历史预约保留；已有未来预约不会自动取消；不能创建新预约，也不能作为修改预约的新目标。',
        `确定停用“${room.name}”吗？`,
        { confirmButtonText: '确认停用', cancelButtonText: '取消', type: 'warning' },
      )
    submitting.value = true
    if (disabling) {
      await disableRoom(room.id)
      ElMessage.success('会议室已停用')
    } else {
      await enableRoom(room.id)
      ElMessage.success('会议室已启用')
    }
    await invalidateRooms()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close')
      ElMessage.error((error as ApiError).message ?? '操作失败，请稍后重试。')
  } finally {
    submitting.value = false
  }
}
</script>
<template>
  <section class="admin-page">
    <div class="page-title">
      <h1>会议室管理</h1>
      <el-button type="primary" @click="openCreate">新增会议室</el-button>
    </div>
    <LoadingState v-if="roomsQuery.isPending.value" /><ErrorState
      v-else-if="roomsQuery.isError.value"
      :error="roomsQuery.error.value"
      @retry="roomsQuery.refetch()"
    /><template v-else
      ><div class="mobile-room-cards">
        <article v-for="room in roomsQuery.data.value ?? []" :key="room.id" class="room-card">
          <strong>{{ room.name }}</strong
          ><span>{{ room.location }}</span
          ><span>容量：{{ room.capacity }} 人</span
          ><span>状态：{{ room.status === 'ENABLED' ? '启用' : '已停用' }}</span
          ><span>排序：{{ room.sortOrder }}</span>
          <div>
            <el-button link type="primary" @click="openEdit(room)">编辑</el-button
            ><el-button
              link
              :type="room.status === 'ENABLED' ? 'danger' : 'success'"
              :loading="submitting"
              @click="toggleRoom(room)"
              >{{ room.status === 'ENABLED' ? '停用' : '启用' }}</el-button
            >
          </div>
        </article>
      </div>
      <el-table class="desktop-room-table" :data="roomsQuery.data.value ?? []"
        ><el-table-column prop="name" label="名称" min-width="160" /><el-table-column
          prop="location"
          label="位置"
          min-width="160"
        /><el-table-column prop="capacity" label="容量" width="90" /><el-table-column
          prop="facilitiesText"
          label="设施"
          min-width="160"
        /><el-table-column prop="usageNotice" label="使用须知" min-width="180" /><el-table-column
          label="状态"
          width="100"
          ><template #default="{ row }"
            ><el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">{{
              row.status === 'ENABLED' ? '启用' : '已停用'
            }}</el-tag></template
          ></el-table-column
        ><el-table-column prop="sortOrder" label="排序" width="80" /><el-table-column
          label="操作"
          width="160"
          ><template #default="{ row }"
            ><el-button link type="primary" @click="openEdit(row)">编辑</el-button
            ><el-button
              link
              :type="row.status === 'ENABLED' ? 'danger' : 'success'"
              :loading="submitting"
              @click="toggleRoom(row)"
              >{{ row.status === 'ENABLED' ? '停用' : '启用' }}</el-button
            ></template
          ></el-table-column
        ></el-table
      ></template
    ><el-dialog
      v-model="formVisible"
      :title="editingRoom ? '修改会议室' : '新增会议室'"
      width="480px"
      @closed="editingRoom = undefined"
      ><AdminRoomForm
        v-if="formVisible"
        :room="editingRoom"
        :submitting="submitting"
        @cancel="closeForm"
        @submit="saveRoom"
    /></el-dialog>
  </section>
</template>
<style scoped>
.admin-page {
  display: grid;
  gap: 20px;
}
.page-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
h1 {
  margin: 0;
}
.mobile-room-cards {
  display: none;
}
@media (max-width: 760px) {
  h1 {
    font-size: 20px;
  }
  .desktop-room-table {
    display: none;
  }
  .mobile-room-cards {
    display: grid;
    gap: 12px;
  }
  .room-card {
    display: grid;
    gap: 6px;
    padding: 14px;
    border: 1px solid #cbd5e1;
    border-radius: 8px;
    background: #fff;
  }
  .room-card > div {
    display: flex;
    gap: 8px;
  }
  .room-card .el-button {
    min-height: 40px;
  }
}
</style>
