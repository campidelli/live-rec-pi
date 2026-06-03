import { api } from '../api/client'
import type { Status, Session } from '../api/types'

interface Props {
  status: Status | null
  selected: Set<number>
  sessions: Session[]
}

export function PlaybackBar({ status, selected, sessions }: Props) {
  const isPlaying = status?.playing ?? false
  const isPaused = status?.paused ?? false
  const progress = status && status.duration > 0
    ? (status.position / status.duration) * 100
    : 0

  async function playSelected() {
    if (selected.size === 0 || sessions.length === 0) return
    const lastSession = sessions[sessions.length - 1]
    const selectedFiles = lastSession.files.filter((f) => {
      const match = f.name.match(/^(\d+)-/)
      if (!match) return false
      return selected.has(parseInt(match[1], 10) - 1)
    })
    await Promise.all(
      selectedFiles.map((f) => api.play(lastSession.session, f.name)),
    )
  }

  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center gap-3 flex-wrap">
        <button
          onClick={playSelected}
          disabled={isPlaying || selected.size === 0}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded text-sm bg-blue-700 text-white hover:bg-blue-600 disabled:opacity-40 transition-colors"
        >
          ▶ Play selected
        </button>

        {isPlaying && !isPaused && (
          <button
            onClick={() => api.pause()}
            className="px-3 py-1.5 rounded text-sm bg-zinc-700 text-white hover:bg-zinc-600 transition-colors"
          >
            ⏸ Pause
          </button>
        )}

        {isPaused && (
          <button
            onClick={() => api.resume()}
            className="px-3 py-1.5 rounded text-sm bg-zinc-700 text-white hover:bg-zinc-600 transition-colors"
          >
            ▶ Resume
          </button>
        )}

        <button
          onClick={() => api.playbackStop()}
          disabled={!isPlaying}
          className="px-3 py-1.5 rounded text-sm bg-zinc-700 text-white hover:bg-zinc-600 disabled:opacity-40 transition-colors"
        >
          ■ Stop
        </button>
      </div>

      {isPlaying && (
        <div className="h-1.5 w-full rounded bg-zinc-700 overflow-hidden">
          <div
            className="h-full rounded bg-blue-500 transition-all duration-300"
            style={{ width: `${progress}%` }}
          />
        </div>
      )}
    </div>
  )
}
