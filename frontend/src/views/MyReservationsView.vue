<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'

import ErrorState from '@/components/common/ErrorState.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import MyBookingDetailDrawer from '@/components/reservation/MyBookingDetailDrawer.vue'
import { myBookingsQueryOptions } from '@/queries/bookings'
import type { BookingDetail, BookingFilterStatus } from '@/types/booking'
import { formatTimeRange } from '@/utils/schedule'

const page = ref(1)
const size = 20
const selectedId = ref<number>()
const editOnOpen = ref(false)
const filters = reactive<{ status?: BookingFilterStatus; date?: string }>({})
const queryClient = useQueryClient()
const listQuery = useQuery(computed(() => myBookingsQueryOptions(page.value, size, { ...filters })))
const detailVisible = computed({
  get: () => selectedId.value !== undefined,
  set: (visible) => {
    if (!visible) {
      selectedId.value = undefined
      editOnOpen.value = false
    }
  },
})

watch(filters, () => {
  page.value = 1
})

function statusText(value: BookingDetail) {
  return value.status === 'CANCELLED'
    ? '已取消'
    : { UPCOMING: '未开始', IN_PROGRESS: '进行中', ENDED: '已结束' }[value.displayStatus]
}
function openDetail(id: number) {
  editOnOpen.value = false
  selectedId.value = id
}
function resetFilters() {
  filters.status = undefined
  filters.date = undefined
  page.value = 1
}
async function openEdit(id: number) {
  editOnOpen.value = true
  selectedId.value = id
  await queryClient.invalidateQueries({ queryKey: ['booking-detail', id] })
}
</script>

<template>
  <section class="reservations-page">
    <h1>我的预约</h1>
    <div class="filters">
      <el-select
        v-model="filters.status"
        class="filter-status"
        clearable
        placeholder="全部状态"
        aria-label="状态筛选"
      >
        <el-option label="即将开始" value="UPCOMING" />
        <el-option label="进行中" value="IN_PROGRESS" />
        <el-option label="已结束" value="ENDED" />
        <el-option label="已取消" value="CANCELLED" />
      </el-select>

      <el-date-picker
        v-model="filters.date"
        class="filter-date"
        type="date"
        value-format="YYYY-MM-DD"
        :editable="false"
        clearable
        placeholder="选择日期"
        aria-label="日期筛选"
      />

      <el-button @click="resetFilters">重置</el-button>
    </div>
    <LoadingState v-if="listQuery.isPending.value" />
    <ErrorState
      v-else-if="listQuery.isError.value"
      :error="listQuery.error.value"
      @retry="listQuery.refetch()"
    />
    <el-empty v-else-if="listQuery.data.value?.items.length === 0" description="暂无预约" />
    <template v-else-if="listQuery.data.value">
      <div class="mobile-reservation-list">
        <article v-for="item in listQuery.data.value.items" :key="item.id" class="reservation-card">
          <strong>{{ item.subject }}</strong
          ><span>{{ item.room.name }}</span>
          <span
            >{{ item.startTime.slice(0, 10) }}
            {{ formatTimeRange(item.startTime, item.endTime) }}</span
          >
          <span>状态：{{ statusText(item) }}</span>
          <div>
            <el-button link type="primary" @click="openDetail(item.id)">查看</el-button
            ><el-button
              v-if="item.status === 'ACTIVE' && item.displayStatus === 'UPCOMING'"
              link
              type="primary"
              @click="openEdit(item.id)"
              >修改</el-button
            >
            <el-button
              v-if="item.status === 'ACTIVE' && item.displayStatus !== 'ENDED'"
              link
              type="danger"
              @click="openDetail(item.id)"
              >取消</el-button
            >
          </div>
        </article>
      </div>
      <el-table class="desktop-reservation-table" :data="listQuery.data.value.items">
        <el-table-column prop="subject" label="会议主题" min-width="180" />
        <el-table-column label="会议室" min-width="150"
          ><template #default="{ row }">{{ row.room.name }}</template></el-table-column
        >
        <el-table-column label="日期" width="120"
          ><template #default="{ row }">{{ row.startTime.slice(0, 10) }}</template></el-table-column
        >
        <el-table-column label="时间" width="150"
          ><template #default="{ row }">{{
            formatTimeRange(row.startTime, row.endTime)
          }}</template></el-table-column
        >
        <el-table-column label="状态" width="100"
          ><template #default="{ row }">{{ statusText(row) }}</template></el-table-column
        >
        <el-table-column label="操作" width="190"
          ><template #default="{ row }"
            ><el-button link type="primary" @click="openDetail(row.id)">查看</el-button
            ><el-button
              v-if="row.status === 'ACTIVE' && row.displayStatus === 'UPCOMING'"
              link
              type="primary"
              @click="openEdit(row.id)"
              >修改</el-button
            ><el-button
              v-if="row.status === 'ACTIVE' && row.displayStatus !== 'ENDED'"
              link
              type="danger"
              @click="openDetail(row.id)"
              >取消</el-button
            ></template
          ></el-table-column
        >
      </el-table>
      <el-pagination
        v-if="listQuery.data.value.total > size"
        class="pagination"
        layout="prev, pager, next"
        :current-page="page"
        :page-size="size"
        :total="listQuery.data.value.total"
        @current-change="page = $event"
      />
    </template>
    <MyBookingDetailDrawer
      v-model="detailVisible"
      :booking-id="selectedId"
      :edit-on-open="editOnOpen"
    />
  </section>
</template>

<style scoped>
.reservations-page {
  display: grid;
  gap: 20px;
}
h1 {
  margin: 0;
}
.filters {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.filter-status {
  width: 160px;
}

.filter-date {
  width: 180px;
}
.pagination {
  justify-content: flex-end;
  margin-top: 16px;
}
.mobile-reservation-list {
  display: none;
}
@media (max-width: 760px) {
  h1 {
    font-size: 20px;
  }
  .desktop-reservation-table {
    display: none;
  }
  .mobile-reservation-list {
    display: grid;
    gap: 12px;
  }
  .reservation-card {
    display: grid;
    gap: 6px;
    padding: 14px;
    border: 1px solid #cbd5e1;
    border-radius: 8px;
    background: #fff;
  }
  .reservation-card strong,
  .reservation-card span {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .reservation-card strong {
    display: -webkit-box;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
    white-space: normal;
  }
  .reservation-card span {
    white-space: nowrap;
  }
  .reservation-card > div {
    display: flex;
    gap: 8px;
  }
  .reservation-card .el-button {
    min-height: 40px;
  }
  .filters {
    display: grid;
  }
  .filter-status,
  .filter-date {
    width: 100%;
  }
}
</style>
