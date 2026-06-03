import { useWebSocket } from '../api/websocket'
import type { Status } from '../api/types'

export function useStatus(): { status: Status | null; wsConnected: boolean } {
  const { status, connected } = useWebSocket()
  return { status, wsConnected: connected }
}
