import { QueryClient } from '@tanstack/vue-query'
import { createRouter, createWebHistory } from 'vue-router'

import AppLayout from '@/layouts/AppLayout.vue'
import { currentUserQueryOptions } from '@/queries/currentUser'
import AdminReservationsView from '@/views/admin/AdminReservationsView.vue'
import AdminRoomsView from '@/views/admin/AdminRoomsView.vue'
import MyReservationsView from '@/views/MyReservationsView.vue'
import ScheduleView from '@/views/ScheduleView.vue'

const queryClient = new QueryClient()

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: AppLayout,
      children: [
        { path: '', redirect: '/schedule' },
        { path: 'schedule', component: ScheduleView },
        { path: 'my-reservations', component: MyReservationsView },
        { path: 'admin', redirect: '/admin/reservations', meta: { requiresAdmin: true } },
        {
          path: 'admin/reservations',
          component: AdminReservationsView,
          meta: { requiresAdmin: true },
        },
        { path: 'admin/rooms', component: AdminRoomsView, meta: { requiresAdmin: true } },
      ],
    },
  ],
})

router.beforeEach(async (to) => {
  if (!to.meta.requiresAdmin) return true

  try {
    const currentUser = await queryClient.ensureQueryData(currentUserQueryOptions())
    return currentUser.roleCode === 'ADMIN' ? true : '/schedule'
  } catch {
    return '/schedule'
  }
})

// Router Guard 仅控制前端体验，不是安全边界；管理员 API 仍须由后端鉴权。
export { queryClient }
export default router
