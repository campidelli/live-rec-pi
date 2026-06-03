import { useEffect, useState } from 'react'
import { api } from './api/client'
import { useStatus } from './hooks/useStatus'
import { useChannels } from './hooks/useChannels'
import { useSessions } from './hooks/useSessions'
import { useMeterWebSocket } from './api/meters'
import type { Channel, Mixer, OutputFormat } from './api/types'
import { MixerSelector } from './components/MixerSelector'
import { StorageInfo } from './components/StorageInfo'
import { ConnectionStatus } from './components/ConnectionStatus'
import { ChannelGrid } from './components/ChannelGrid'
import { RecordingBar } from './components/RecordingBar'
import { PlaybackBar } from './components/PlaybackBar'
import { SessionModal } from './components/SessionModal'

const STORAGE_KEY = 'mixer_host'

export default function App() {
  const { status, wsConnected } = useStatus()
  const [mixers, setMixers] = useState<Mixer[]>([])
  const [formats, setFormats] = useState<OutputFormat[]>([])
  const [activeFormat, setActiveFormat] = useState<string>('wav')
  const [mixerHost, setMixerHost] = useState<string>(
    () => localStorage.getItem(STORAGE_KEY) ?? '',
  )
  const [discovering, setDiscovering] = useState(false)
  const [discoverError, setDiscoverError] = useState<string | null>(null)
  const [showSessionModal, setShowSessionModal] = useState(false)
  const [sessionSuffix, setSessionSuffix] = useState<string | null>(null)

  const mixerId = status?.mixer_id ?? null
  const rawChannels = status?.channels ?? []
  const sessions = useSessions(status)
  const { selected, toggle, toggleAll, rename } = useChannels(mixerId, rawChannels.length)

  const { levels: meterLevels, connected: oscConnected } = useMeterWebSocket(
    mixerId,
    mixerHost || null,
  )

  const channels: Channel[] = rawChannels.map((ch) => ({
    ...ch,
    level: meterLevels.get(ch.index) ?? 0,
  }))

  const usbReady = status?.connected ?? false
  const isRecording = status?.status === 'recording'
  const isPlaying = status?.playing ?? false

  // Full session path is only known once the daemon starts recording
  const recordingSession = status?.recording_session ?? null
  const storagePath = status?.storage_path ?? null
  const fullSessionPath = recordingSession && storagePath
    ? `${storagePath}/${recordingSession}`
    : null

  useEffect(() => {
    api.getMixers().then((r) => setMixers(r.mixers)).catch(() => {})
    api.getFormats().then((r) => setFormats(r.formats)).catch(() => {})
    api.getSettings().then((r) => setActiveFormat(r.settings.format)).catch(() => {})
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

  async function handleFormatChange(e: React.ChangeEvent<HTMLSelectElement>) {
    const fmt = e.target.value
    setActiveFormat(fmt)
    await api.updateSettings({ format: fmt }).catch(() => {})
  }

  function handleSessionConfirm(suffix: string) {
    setSessionSuffix(suffix || null)
    setShowSessionModal(false)
  }

  return (
    <div className="min-h-screen bg-zinc-900 text-white flex flex-col">

      {showSessionModal && (
        <SessionModal
          onConfirm={handleSessionConfirm}
          onCancel={() => setShowSessionModal(false)}
        />
      )}

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
            ) : '⌕'}
            {discovering ? 'Searching…' : 'Find mixer'}
          </button>

          <button
            onClick={() => setShowSessionModal(true)}
            disabled={isRecording}
            className="px-3 py-1 rounded text-sm bg-green-700 text-white hover:bg-green-600 disabled:opacity-40 transition-colors"
          >
            New Session
          </button>

          {formats.length > 0 && (
            <select
              value={activeFormat}
              onChange={handleFormatChange}
              disabled={isRecording}
              className="bg-zinc-700 text-white text-sm rounded px-2 py-1 border border-zinc-600 focus:outline-none focus:ring-1 focus:ring-blue-500 disabled:opacity-40"
            >
              {formats.map((f) => (
                <option key={f.id} value={f.id} title={f.description}>
                  {f.id.toUpperCase()}
                </option>
              ))}
            </select>
          )}

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

      {/* Session info bar */}
      <div className="flex items-center justify-center gap-2 px-4 py-1.5 border-b border-zinc-700 bg-zinc-900">
        {fullSessionPath ? (
          <>
            <span className={`text-xs font-semibold uppercase tracking-wide flex-shrink-0 ${isRecording ? 'text-red-400' : 'text-zinc-500'}`}>
              {isRecording ? 'Recording at' : 'Recorded at'}
            </span>
            <span
              className="text-xs font-mono text-zinc-300 truncate"
              title={fullSessionPath}
            >
              {fullSessionPath}
            </span>
          </>
        ) : sessionSuffix ? (
          <>
            <span className="text-xs font-semibold uppercase tracking-wide text-zinc-500 flex-shrink-0">
              Session
            </span>
            <span className="text-xs font-mono text-zinc-400">{sessionSuffix}</span>
          </>
        ) : (
          <span className="text-xs text-zinc-600">No active session — press New Session to configure</span>
        )}
      </div>

      {/* Channel grid */}
      <main className="flex-1 overflow-y-auto px-4 py-3">
        {channels.length > 0 ? (
          <ChannelGrid
            channels={channels}
            status={status}
            selected={selected}
            onToggle={toggle}
            onToggleAll={toggleAll}
            onRename={rename}
          />
        ) : (
          <div className="flex items-center justify-center h-full text-zinc-500 text-sm">
            {wsConnected ? 'No channels — load a mixer to begin.' : 'Connecting…'}
          </div>
        )}
      </main>

      {/* Footer — playback left, recording right */}
      <footer className="bg-zinc-800 border-t border-zinc-700 px-4 py-3">
        <div className="flex items-end gap-4">
          <PlaybackBar
            status={status}
            selected={selected}
            sessions={sessions}
            isRecording={isRecording}
          />
          <RecordingBar
            status={status}
            selected={selected}
            isPlaying={isPlaying}
          />
        </div>
      </footer>

    </div>
  )
}
