import type { Mixer } from '../api/types'
import { api } from '../api/client'

interface Props {
  mixers: Mixer[]
  onLoaded: () => void
}

export function MixerSelector({ mixers, onLoaded }: Props) {
  const active = mixers.find((m) => m.active)

  async function handleChange(e: React.ChangeEvent<HTMLSelectElement>) {
    await api.loadMixer(e.target.value)
    onLoaded()
  }

  return (
    <select
      value={active?.id ?? ''}
      onChange={handleChange}
      className="bg-zinc-700 text-white text-sm rounded px-3 py-1.5 border border-zinc-600 focus:outline-none focus:ring-1 focus:ring-blue-500"
    >
      {mixers.map((m) => (
        <option key={m.id} value={m.id}>
          {m.name}
        </option>
      ))}
    </select>
  )
}
