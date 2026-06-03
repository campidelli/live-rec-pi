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

function PencilIcon() {
  return (
    <svg viewBox="0 0 16 16" fill="currentColor" className="w-3.5 h-3.5">
      <path d="M12.854.146a.5.5 0 0 0-.707 0L10.5 1.793 14.207 5.5l1.647-1.646a.5.5 0 0 0 0-.708zm.646 6.061L9.793 2.5 3.293 9H3.5a.5.5 0 0 1 .5.5v.5h.5a.5.5 0 0 1 .5.5v.5h.5a.5.5 0 0 1 .5.5v.5h.5a.5.5 0 0 1 .5.5v.207zm-7.468 7.468A.5.5 0 0 1 6 13.5V13h-.5a.5.5 0 0 1-.5-.5V12h-.5a.5.5 0 0 1-.5-.5V11h-.5a.5.5 0 0 1-.5-.5V10h-.5a.5.5 0 0 1-.175-.032l-.179.178a.5.5 0 0 0-.11.168l-2 5a.5.5 0 0 0 .65.65l5-2a.5.5 0 0 0 .168-.11z" />
    </svg>
  )
}

function FloppyIcon() {
  return (
    <svg viewBox="0 0 16 16" fill="currentColor" className="w-3.5 h-3.5">
      <path d="M11 2H9v3h2z" />
      <path d="M1.5 0h11.586a1.5 1.5 0 0 1 1.06.44l1.415 1.414A1.5 1.5 0 0 1 16 2.914V14.5a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 0 14.5v-13A1.5 1.5 0 0 1 1.5 0M1 1.5v13a.5.5 0 0 0 .5.5H2v-4.5A1.5 1.5 0 0 1 3.5 9h9a1.5 1.5 0 0 1 1.5 1.5V15h.5a.5.5 0 0 0 .5-.5V2.914a.5.5 0 0 0-.146-.353l-1.415-1.415A.5.5 0 0 0 13.086 1H13v3.5A1.5 1.5 0 0 1 11.5 6h-7A1.5 1.5 0 0 1 3 4.5V1H1.5a.5.5 0 0 0-.5.5m3 0v3.5a.5.5 0 0 0 .5.5h7a.5.5 0 0 0 .5-.5V1zM3 15h10v-4.5a.5.5 0 0 0-.5-.5h-9a.5.5 0 0 0-.5.5zm7-8.5h1a.5.5 0 0 1 0 1h-1a.5.5 0 0 1 0-1" />
    </svg>
  )
}

export function ChannelRow({ channel, selected, recording, playing, onToggle, onRename }: Props) {
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(channel.id)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (editing) inputRef.current?.focus()
  }, [editing])

  useEffect(() => {
    if (!editing) setDraft(channel.id)
  }, [channel.id, editing])

  function startEdit(e: React.MouseEvent) {
    e.stopPropagation()
    setDraft(channel.id)
    setEditing(true)
  }

  async function commit(e: React.MouseEvent) {
    e.stopPropagation()
    setEditing(false)
    if (draft.trim() && draft.trim() !== channel.id) {
      await onRename(channel.index, draft.trim())
    }
  }

  function cancel(e: React.MouseEvent) {
    e.stopPropagation()
    setDraft(channel.id)
    setEditing(false)
  }

  const activity = recording ? 'border-l-red-500' : playing ? 'border-l-blue-500' : 'border-l-transparent'

  return (
    <div
      onClick={() => !editing && onToggle(channel.index)}
      className={`flex items-center gap-2 px-3 py-2 border-l-4 ${activity} bg-zinc-800 rounded cursor-pointer select-none hover:bg-zinc-750 active:bg-zinc-700 transition-colors`}
    >
      {/* checkbox — visual only; row click handles toggle */}
      <input
        type="checkbox"
        checked={selected}
        onChange={() => {}}
        onClick={(e) => e.stopPropagation()}
        className="w-4 h-4 accent-blue-500 flex-shrink-0 pointer-events-none"
        readOnly
      />

      {/* number */}
      <span className="w-7 text-right font-mono text-sm text-zinc-400 flex-shrink-0">
        {String(channel.index + 1).padStart(2, '0')}
      </span>

      {/* name / inline edit */}
      <div className="flex-1 min-w-0 flex items-center gap-1.5" onClick={(e) => editing && e.stopPropagation()}>
        {editing ? (
          <>
            <input
              ref={inputRef}
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') commit(e as unknown as React.MouseEvent)
                if (e.key === 'Escape') cancel(e as unknown as React.MouseEvent)
              }}
              className="flex-1 min-w-0 bg-zinc-700 text-white rounded px-2 py-0.5 text-sm outline-none focus:ring-1 focus:ring-blue-500"
            />
            <button
              onClick={commit}
              title="Save"
              className="flex-shrink-0 p-1 rounded text-green-400 hover:bg-zinc-600 transition-colors"
            >
              <FloppyIcon />
            </button>
            <button
              onClick={cancel}
              title="Cancel"
              className="flex-shrink-0 p-1 rounded text-zinc-400 hover:bg-zinc-600 transition-colors text-xs leading-none"
            >
              ✕
            </button>
          </>
        ) : (
          <>
            <span className="flex-1 min-w-0 text-sm text-white truncate">
              {channel.id}
              {channel.mixer_name && channel.mixer_name !== channel.id && (
                <span className="ml-1.5 text-xs text-zinc-500">({channel.mixer_name})</span>
              )}
            </span>
            <button
              onClick={startEdit}
              title="Edit name"
              className="flex-shrink-0 p-1 rounded text-zinc-500 hover:text-zinc-300 hover:bg-zinc-600 transition-colors"
            >
              <PencilIcon />
            </button>
          </>
        )}
      </div>

      {/* meter */}
      <div className="w-24 flex-shrink-0">
        <ChannelMeter level={channel.level ?? 0} />
      </div>
    </div>
  )
}
