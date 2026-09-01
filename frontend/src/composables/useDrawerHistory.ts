import { onBeforeUnmount, onMounted, watch, type WritableComputedRef } from 'vue'

export function useDrawerHistory(visible: WritableComputedRef<boolean>) {
  let hasHistoryEntry = false
  let closingFromHistory = false

  function handlePopState() {
    if (!hasHistoryEntry) return
    closingFromHistory = true
    visible.value = false
    hasHistoryEntry = false
    closingFromHistory = false
  }

  watch(visible, (isVisible) => {
    if (isVisible && !hasHistoryEntry) {
      window.history.pushState({ ...window.history.state, drawerOpen: true }, '')
      hasHistoryEntry = true
      return
    }

    if (!isVisible && hasHistoryEntry && !closingFromHistory) {
      window.history.back()
      hasHistoryEntry = false
    }
  })

  onMounted(() => window.addEventListener('popstate', handlePopState))
  onBeforeUnmount(() => window.removeEventListener('popstate', handlePopState))
}
