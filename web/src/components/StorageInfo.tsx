interface Props {
  freeBytes: number
  totalBytes: number
}

function fmt(bytes: number): string {
  if (bytes >= 1e9) return `${(bytes / 1e9).toFixed(1)} GB`
  if (bytes >= 1e6) return `${(bytes / 1e6).toFixed(0)} MB`
  return `${(bytes / 1e3).toFixed(0)} KB`
}

export function StorageInfo({ freeBytes, totalBytes }: Props) {
  const pct = totalBytes > 0 ? ((totalBytes - freeBytes) / totalBytes) * 100 : 0
  const color = pct > 90 ? 'bg-red-500' : pct > 75 ? 'bg-yellow-400' : 'bg-green-500'

  return (
    <div className="flex items-center gap-2">
      <div className="h-2 w-20 rounded bg-zinc-700 overflow-hidden">
        <div className={`h-full rounded ${color}`} style={{ width: `${pct}%` }} />
      </div>
      <span className="text-xs text-zinc-400">{fmt(freeBytes)} free</span>
    </div>
  )
}
