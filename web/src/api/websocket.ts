import { useEffect, useRef, useState } from 'react'
import type { Status } from './types'

const WS_URL = `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`

export function useWebSocket(): { status: Status | null; connected: boolean } {
  const [status, setStatus] = useState<Status | null>(null)
  const [connected, setConnected] = useState(false)
  const wsRef = useRef<WebSocket | null>(null)
  const reconnectTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    function connect() {
      const ws = new WebSocket(WS_URL)
      wsRef.current = ws

      ws.onopen = () => setConnected(true)

      ws.onmessage = (evt) => {
        try {
          setStatus(JSON.parse(evt.data) as Status)
        } catch {
          // ignore malformed frames
        }
      }

      ws.onclose = () => {
        setConnected(false)
        reconnectTimer.current = setTimeout(connect, 2000)
      }

      ws.onerror = () => ws.close()
    }

    connect()

    return () => {
      wsRef.current?.close()
      if (reconnectTimer.current) clearTimeout(reconnectTimer.current)
    }
  }, [])

  return { status, connected }
}
