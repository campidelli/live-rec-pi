import type { Channel, Status } from '../api/types'
import { ChannelRow } from './ChannelRow'

const ROWS_PER_COLUMN = 9

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
      const match = f.match(/\/(\d+)-/)
      return match ? parseInt(match[1], 10) - 1 : -1
    }),
  )

  // split into columns of ROWS_PER_COLUMN
  const columns: Channel[][] = []
  for (let i = 0; i < channels.length; i += ROWS_PER_COLUMN) {
    columns.push(channels.slice(i, i + ROWS_PER_COLUMN))
  }

  return (
    <div className="flex flex-col gap-2">
      {/* select-all header */}
      <div
        className="flex items-center gap-3 px-3 py-1 cursor-pointer select-none"
        onClick={() => onToggleAll(allIndices)}
      >
        <input
          type="checkbox"
          checked={allSelected}
          onChange={() => {}}
          className="w-4 h-4 accent-blue-500 pointer-events-none"
          readOnly
        />
        <span className="text-xs text-zinc-500 uppercase tracking-wide">
          {selected.size} of {channels.length} selected
        </span>
      </div>

      {/* columns */}
      <div className="flex gap-2">
        {columns.map((col, colIdx) => (
          <div key={colIdx} className="flex-1 flex flex-col gap-1 min-w-0">
            {col.map((ch) => (
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
        ))}
      </div>
    </div>
  )
}
