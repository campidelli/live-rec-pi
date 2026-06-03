interface Props {
  usbReady: boolean       // daemon sees the mixer via ALSA
  oscConnected: boolean   // OSC meter stream is receiving frames
  mixerHost: string | null
}

interface PillProps {
  label: string
  ok: boolean
  detail?: string
}

function Pill({ label, ok, detail }: PillProps) {
  const color = ok ? 'bg-green-600' : 'bg-zinc-600'
  const dot = ok ? 'bg-green-300' : 'bg-zinc-400'
  return (
    <span className={`inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-xs font-medium text-white ${color}`}>
      <span className={`w-1.5 h-1.5 rounded-full ${ok ? 'animate-pulse' : ''} ${dot}`} />
      {label}
      {detail && <span className="opacity-70">{detail}</span>}
    </span>
  )
}

export function ConnectionStatus({ usbReady, oscConnected, mixerHost }: Props) {
  return (
    <div className="flex items-center gap-2">
      <Pill label="USB" ok={usbReady} />
      <Pill label="OSC" ok={oscConnected} detail={oscConnected && mixerHost ? mixerHost : undefined} />
    </div>
  )
}
