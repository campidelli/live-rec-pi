import type { Channel, Status } from '../api/types'
import { ChannelRow } from './ChannelRow'

interface Props {
  channels: Channel[]
  status: Status | null
  selected: Set<number>
  onToggle: (index: number) => void
  onToggleAll: (indices: number[]) => void
  onRename: (index: number, id: string) => Promise<void>
}

export function ChannelGrid({ channels, status, selected, onToggle, onToggleAll, onRename }: Props) {
  const allIndices = channels.map((c) => c.index)
  const allSelected = allIndices.length > 0 && allIndices.every((i) => selected.has(i))

  const recordingFiles = new Set(
    (status?.recording_files ?? []).map((f) => {
      // extract channel index from filename like "01-kick.wav" → 0
      const match = f.match(/\/(\d+)-/)
      return match ? parseInt(match[1], 10) - 1 : -1
    }),
  )

  return (
    <div className="flex flex-col gap-1">
      {/* select-all header */}
      <div className="flex items-center gap-3 px-3 py-1">
        <input
          type="checkbox"
          checked={allSelected}
          onChange={() => onToggleAll(allIndices)}
          className="w-5 h-5 accent-blue-500 cursor-pointer flex-shrink-0"
        />
        <span className="text-xs text-zinc-500 uppercase tracking-wide">
          {selected.size} of {channels.length} selected
        </span>
      </div>

      {channels.map((ch) => (
        <ChannelRow
          key={ch.index}
          channel={ch}
          selected={selected.has(ch.index)}
          recording={status?.status === 'recording' && recordingFiles.has(ch.index)}
          playing={status?.playing === true && selected.has(ch.index)}
          onToggle={onToggle}
          onRename={onRename}
        />
      ))}
    </div>
  )
}
