import { useEffect, useRef, useState } from 'react'

interface MeterLevel {
  index: number
  level: number
}

function buildWsUrl(mixerId: string, host: string): string {
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  return `${proto}://${location.host}/ws/meters?mixer_id=${encodeURIComponent(mixerId)}&host=${encodeURIComponent(host)}`
}

interface MeterState {
  levels: Map<number, number>
  connected: boolean
}

export function useMeterWebSocket(
  mixerId: string | null,
  host: string | null,
): MeterState {
  const [levels, setLevels] = useState<Map<number, number>>(new Map())
  const [connected, setConnected] = useState(false)
  const wsRef = useRef<WebSocket | null>(null)
  const reconnectTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const lastFrameTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    if (!mixerId || !host) {
      setLevels(new Map())
      setConnected(false)
      return
    }

    function markConnected() {
      setConnected(true)
      if (lastFrameTimer.current) clearTimeout(lastFrameTimer.current)
      // consider disconnected if no frame arrives within 2s
      lastFrameTimer.current = setTimeout(() => setConnected(false), 2000)
    }

    function connect() {
      const ws = new WebSocket(buildWsUrl(mixerId!, host!))
      wsRef.current = ws

      ws.onmessage = (evt) => {
        try {
          const data = JSON.parse(evt.data) as { meters?: MeterLevel[]; error?: string }
          if (data.meters) {
            markConnected()
            setLevels((prev) => {
              const next = new Map(prev)
              for (const { index, level } of data.meters!) next.set(index, level)
              return next
            })
          }
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
      if (lastFrameTimer.current) clearTimeout(lastFrameTimer.current)
    }
  }, [mixerId, host])

  return { levels, connected }
}
