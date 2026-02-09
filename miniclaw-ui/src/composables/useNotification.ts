import { useRouter } from 'vue-router'
import { useWebSocket } from './useWebSocket'

let isSetup = false

export function useNotification() {
  if (isSetup) return
  isSetup = true

  const router = useRouter()
  const { onEvent } = useWebSocket()

  // Request notification permission
  if ('Notification' in window && Notification.permission === 'default') {
    Notification.requestPermission()
  }

  onEvent('tool.confirm_request', (event) => {
    const isOnWorkspace = router.currentRoute.value.path === '/'
    const isHidden = document.visibilityState === 'hidden'

    if (!isOnWorkspace || isHidden) {
      if ('Notification' in window && Notification.permission === 'granted') {
        const toolName = event.payload?.toolName || 'Tool'
        const n = new Notification('MiniClaw - Action Required', {
          body: `${toolName} requires your approval`,
          tag: 'tool-confirm'
        })
        n.onclick = () => {
          window.focus()
          router.push('/')
          n.close()
        }
      }
    }
  })
}
