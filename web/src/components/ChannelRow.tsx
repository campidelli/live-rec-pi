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

const btnBase = 'flex-shrink-0 px-2 py-0.5 rounded text-xs font-medium transition-colors'

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

  function restore(e: React.MouseEvent) {
    e.stopPropagation()
    // OSC name takes precedence over daemon config name
    const canonical = channel.mixer_name?.trim() || channel.id
    setDraft(canonical)
  }

  async function save(e: React.MouseEvent) {
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
                if (e.key === 'Enter') save(e as unknown as React.MouseEvent)
                if (e.key === 'Escape') cancel(e as unknown as React.MouseEvent)
              }}
              className="flex-1 min-w-0 bg-zinc-700 text-white rounded px-2 py-0.5 text-sm outline-none focus:ring-1 focus:ring-blue-500"
            />
            <button onClick={restore} className={`${btnBase} text-zinc-400 hover:text-white hover:bg-zinc-600`}>
              Restore
            </button>
            <button onClick={save} className={`${btnBase} text-green-400 hover:text-white hover:bg-green-700`}>
              Save
            </button>
            <button onClick={cancel} className={`${btnBase} text-red-400 hover:text-white hover:bg-red-700`}>
              Cancel
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
            <button onClick={startEdit} className={`${btnBase} text-zinc-500 hover:text-zinc-300 hover:bg-zinc-600`}>
              Edit
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
