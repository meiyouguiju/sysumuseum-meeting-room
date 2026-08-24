import { defineStore } from 'pinia'
import { ref } from 'vue'

// `/me` 始终由 Vue Query 管理，是用户身份的唯一事实来源。
export const useUserUiStore = defineStore('user-ui', () => {
  const adminMenuOpen = ref(false)
  return { adminMenuOpen }
})
