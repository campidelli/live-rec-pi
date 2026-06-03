interface Props {
  level: number // 0.0–1.0
}

export function ChannelMeter({ level }: Props) {
  const pct = Math.min(1, Math.max(0, level)) * 100
  const color =
    pct > 85 ? 'bg-red-500' : pct > 65 ? 'bg-yellow-400' : 'bg-green-500'

  return (
    <div className="h-2 w-full rounded bg-zinc-700 overflow-hidden">
      <div
        className={`h-full rounded transition-all duration-75 ${color}`}
        style={{ width: `${pct}%` }}
      />
    </div>
  )
}
