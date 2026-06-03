import { api } from '../api/client'
import type { Status } from '../api/types'

interface Props {
  status: Status | null
  selected: Set<number>
  isPlaying: boolean
}

function formatTime(seconds: number): string {
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = Math.floor(seconds % 60)
  return h > 0
    ? `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
    : `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

export function RecordingBar({ status, selected, isPlaying }: Props) {
  const isRecording = status?.status === 'recording'
  const canRecord = selected.size > 0 && !isRecording && !isPlaying

  return (
    <div className="flex items-center gap-2 justify-end">
      {isRecording && (
        <span className="font-mono text-sm text-red-400 mr-1">
          {formatTime(status?.record_time ?? 0)}
        </span>
      )}

      <button
        onClick={() => api.startRecording()}
        disabled={!canRecord}
        title={
          isPlaying ? 'Stop playback before recording' :
          selected.size === 0 ? 'Select at least one channel' : undefined
        }
        className="flex items-center gap-1.5 px-3 py-1.5 rounded text-sm bg-red-700 text-white hover:bg-red-600 disabled:opacity-40 transition-colors"
      >
        <span className={`w-2 h-2 rounded-full bg-red-300 ${isRecording ? 'animate-pulse' : ''}`} />
        REC
      </button>

      <button
        onClick={() => api.stopRecording()}
        disabled={!isRecording}
        className="px-3 py-1.5 rounded text-sm bg-zinc-700 text-white hover:bg-zinc-600 disabled:opacity-40 transition-colors"
      >
        ■ Stop
      </button>
    </div>
  )
}
