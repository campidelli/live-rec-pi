import { useState } from 'react'
import { api } from '../api/client'
import { SessionModal } from './SessionModal'
import type { Status } from '../api/types'

interface Props {
  status: Status | null
}

function formatTime(seconds: number): string {
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = Math.floor(seconds % 60)
  return h > 0
    ? `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
    : `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

export function RecordingBar({ status }: Props) {
  const [showModal, setShowModal] = useState(false)
  const isRecording = status?.status === 'recording'

  async function handleStart(_nameSuffix: string) {
    setShowModal(false)
    // nameSuffix is UI-only for now; session naming is handled daemon-side via template
    await api.startRecording()
  }

  return (
    <>
      {showModal && (
        <SessionModal onConfirm={handleStart} onCancel={() => setShowModal(false)} />
      )}

      <div className="flex items-center gap-3 flex-wrap">
        <button
          onClick={() => setShowModal(true)}
          disabled={isRecording}
          className="px-3 py-1.5 rounded text-sm bg-zinc-700 text-white hover:bg-zinc-600 disabled:opacity-40 transition-colors"
        >
          New Session
        </button>

        <button
          onClick={() => api.startRecording()}
          disabled={isRecording}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded text-sm bg-red-700 text-white hover:bg-red-600 disabled:opacity-40 transition-colors"
        >
          <span className="w-2 h-2 rounded-full bg-red-300 animate-pulse" />
          REC
        </button>

        <button
          onClick={() => api.stopRecording()}
          disabled={!isRecording}
          className="px-3 py-1.5 rounded text-sm bg-zinc-700 text-white hover:bg-zinc-600 disabled:opacity-40 transition-colors"
        >
          ■ Stop
        </button>

        {isRecording && (
          <span className="font-mono text-sm text-red-400">
            {formatTime(status?.record_time ?? 0)}
          </span>
        )}
      </div>
    </>
  )
}
