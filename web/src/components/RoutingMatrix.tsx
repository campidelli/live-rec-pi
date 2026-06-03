import { useState } from 'react'
import type { Channel } from '../api/types'

interface Props {
  channels: Channel[]
}

export function RoutingMatrix({ channels }: Props) {
  const [open, setOpen] = useState(false)

  return (
    <div className="bg-zinc-800 rounded overflow-hidden">
      <button
        onClick={() => setOpen((v) => !v)}
        className="w-full flex items-center justify-between px-4 py-2.5 text-sm text-zinc-300 hover:bg-zinc-700 transition-colors"
      >
        <span className="font-medium">Routing Matrix</span>
        <span className="text-zinc-500">{open ? '▲' : '▼'}</span>
      </button>

      {open && (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-zinc-500 text-xs uppercase tracking-wide border-b border-zinc-700">
                <th className="px-4 py-2 text-left w-12">Ch</th>
                <th className="px-4 py-2 text-left">Channel ID</th>
                <th className="px-4 py-2 text-left w-16">Out</th>
              </tr>
            </thead>
            <tbody>
              {channels.map((ch) => (
                <tr key={ch.index} className="border-b border-zinc-700/50 hover:bg-zinc-700/30">
                  <td className="px-4 py-1.5 font-mono text-zinc-400">
                    {String(ch.index + 1).padStart(2, '0')}
                  </td>
                  <td className="px-4 py-1.5 text-white">{ch.id}</td>
                  <td className="px-4 py-1.5 font-mono text-zinc-400">
                    {String(ch.index + 1).padStart(2, '0')}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
