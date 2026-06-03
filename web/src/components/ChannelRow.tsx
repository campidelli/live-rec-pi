import { useState, useRef, useEffect } from 'react'
import type { Channel } from '../api/types'
import { ChannelMeter } from './ChannelMeter'

interface Props {
  channel: Channel
  selected: boolean
  recording: boolean
  playing: boolean
  onToggle: (index: number) => void
  onRename: (index: number, id: string) => Promise<void>
}

export function ChannelRow({ channel, selected, recording, playing, onToggle, onRename }: Props) {
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(channel.id)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (editing) inputRef.current?.focus()
  }, [editing])

  // keep draft in sync if status update arrives while not editing
  useEffect(() => {
    if (!editing) setDraft(channel.id)
  }, [channel.id, editing])

  function startEdit() {
    setDraft(channel.id)
    setEditing(true)
  }

  async function commit() {
    setEditing(false)
    if (draft.trim() && draft.trim() !== channel.id) {
      await onRename(channel.index, draft.trim())
    }
  }

  function cancel() {
    setDraft(channel.id)
    setEditing(false)
  }

  const activity = recording ? 'border-l-red-500' : playing ? 'border-l-blue-500' : 'border-l-transparent'

  return (
    <div className={`flex items-center gap-3 px-3 py-2 border-l-4 ${activity} bg-zinc-800 rounded`}>
      {/* checkbox */}
      <input
        type="checkbox"
        checked={selected}
        onChange={() => onToggle(channel.index)}
        className="w-5 h-5 accent-blue-500 cursor-pointer flex-shrink-0"
      />

      {/* number */}
      <span className="w-7 text-right font-mono text-sm text-zinc-400 flex-shrink-0">
        {String(channel.index + 1).padStart(2, '0')}
      </span>

      {/* name / inline edit */}
      <div className="flex-1 min-w-0">
        {editing ? (
          <input
            ref={inputRef}
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onBlur={commit}
            onKeyDown={(e) => {
              if (e.key === 'Enter') commit()
              if (e.key === 'Escape') cancel()
            }}
            className="w-full bg-zinc-700 text-white rounded px-2 py-0.5 text-sm outline-none focus:ring-1 focus:ring-blue-500"
          />
        ) : (
          <button
            onClick={startEdit}
            className="w-full text-left text-sm text-white truncate hover:text-blue-400 transition-colors"
          >
            {channel.id}
            {channel.mixer_name && channel.mixer_name !== channel.id && (
              <span className="ml-2 text-xs text-zinc-500">({channel.mixer_name})</span>
            )}
          </button>
        )}
      </div>

      {/* meter */}
      <div className="w-28 flex-shrink-0">
        <ChannelMeter level={channel.level ?? 0} />
      </div>
    </div>
  )
}
