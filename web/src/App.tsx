import { useEffect, useState } from 'react'
import { api } from './api/client'
import { useStatus } from './hooks/useStatus'
import { useChannels } from './hooks/useChannels'
import { useSessions } from './hooks/useSessions'
import { useMeterWebSocket } from './api/meters'
import type { Channel, Mixer } from './api/types'
import { MixerSelector } from './components/MixerSelector'
import { StorageInfo } from './components/StorageInfo'
import { ConnectionStatus } from './components/ConnectionStatus'
import { ChannelGrid } from './components/ChannelGrid'
import { RecordingBar } from './components/RecordingBar'
import { PlaybackBar } from './components/PlaybackBar'

const STORAGE_KEY = 'mixer_host'

export default function App() {
  const { status, wsConnected } = useStatus()
  const [mixers, setMixers] = useState<Mixer[]>([])
  const [mixerHost, setMixerHost] = useState<string>(
    () => localStorage.getItem(STORAGE_KEY) ?? '',
  )
  const [discovering, setDiscovering] = useState(false)
  const [discoverError, setDiscoverError] = useState<string | null>(null)

  const mixerId = status?.mixer_id ?? null
  const rawChannels = status?.channels ?? []
  const sessions = useSessions(status)
  const { selected, toggle, toggleAll, rename } = useChannels(mixerId)

  const { levels: meterLevels, connected: oscConnected } = useMeterWebSocket(
    mixerId,
    mixerHost || null,
  )

  const channels: Channel[] = rawChannels.map((ch) => ({
    ...ch,
    level: meterLevels.get(ch.index) ?? 0,
  }))

  const usbReady = status?.connected ?? false

  useEffect(() => {
    api.getMixers().then((r) => setMixers(r.mixers)).catch(() => {})
  }, [])

  async function handleDiscover() {
    if (!mixerId) return
    setDiscovering(true)
    setDiscoverError(null)
    try {
      const { host } = await api.discoverMixer(mixerId)
      setMixerHost(host)
      localStorage.setItem(STORAGE_KEY, host)
    } catch (e) {
      setDiscoverError(e instanceof Error ? e.message : 'Not found')
    } finally {
      setDiscovering(false)
    }
  }

  return (
    <div className="min-h-screen bg-zinc-900 text-white flex flex-col">
      {/* Header */}
      <header className="flex items-center justify-between gap-4 px-4 py-2 bg-zinc-800 border-b border-zinc-700 flex-wrap">
        <div className="flex items-center gap-3 flex-wrap">
          <span className="font-semibold text-sm tracking-wide">live-rec-pi</span>

          {mixers.length > 0 && (
            <MixerSelector
              mixers={mixers}
              onLoaded={() => api.getMixers().then((r) => setMixers(r.mixers))}
            />
          )}

          <button
            onClick={handleDiscover}
            disabled={discovering || !mixerId}
            className="flex items-center gap-1.5 px-3 py-1 rounded text-sm bg-zinc-700 hover:bg-zinc-600 disabled:opacity-40 transition-colors"
          >
            {discovering ? (
              <span className="w-3 h-3 border-2 border-white/30 border-t-white rounded-full animate-spin" />
            ) : (
              '⌕'
            )}
            {discovering ? 'Searching…' : 'Find mixer'}
          </button>

          {mixerHost && (
            <span className="text-xs text-zinc-400 font-mono">{mixerHost}</span>
          )}

          {discoverError && (
            <span className="text-xs text-red-400">{discoverError}</span>
          )}
        </div>

        <div className="flex items-center gap-4">
          <ConnectionStatus
            usbReady={usbReady}
            oscConnected={oscConnected}
            mixerHost={mixerHost || null}
          />
          {status && !wsConnected && (
            <span className="text-xs text-red-400">WS offline</span>
          )}
          {status && (
            <StorageInfo
              freeBytes={status.storage_free_bytes}
              totalBytes={status.storage_total_bytes}
            />
          )}
        </div>
      </header>

      {/* Channel grid */}
      <main className="flex-1 overflow-y-auto px-4 py-3 flex flex-col gap-3">
        {channels.length > 0 ? (
          <ChannelGrid
            channels={channels}
            status={status}
            selected={selected}
            onToggle={toggle}
            onToggleAll={toggleAll}
            onRename={rename}
          />
        ) : (          <div className="flex items-center justify-center flex-1 text-zinc-500 text-sm">
            {wsConnected ? 'No channels — load a mixer to begin.' : 'Connecting…'}
          </div>
        )}
      </main>

      {/* Bottom bar */}
      <footer className="bg-zinc-800 border-t border-zinc-700 px-4 py-3 flex flex-col gap-3">
        <RecordingBar status={status} selected={selected} />
        <PlaybackBar status={status} selected={selected} sessions={sessions} />
      </footer>
    </div>
  )
}
