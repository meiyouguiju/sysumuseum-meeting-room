import { onBeforeUnmount, onMounted, ref } from 'vue'

const MOBILE_QUERY = '(max-width: 760px)'

export function useMobileBreakpoint() {
  const isMobile = ref(false)
  let mediaQuery: MediaQueryList | undefined

  function update() {
    isMobile.value = mediaQuery?.matches ?? false
  }

  onMounted(() => {
    mediaQuery = window.matchMedia(MOBILE_QUERY)
    update()
    mediaQuery.addEventListener('change', update)
  })
  onBeforeUnmount(() => mediaQuery?.removeEventListener('change', update))

  return { isMobile }
}
