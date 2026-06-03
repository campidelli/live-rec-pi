import { useState } from 'react'

interface Props {
  onConfirm: (nameSuffix: string) => void
  onCancel: () => void
}

export function SessionModal({ onConfirm, onCancel }: Props) {
  const [name, setName] = useState('')

  function handleKeyDown(e: React.KeyboardEvent) {
    if (e.key === 'Enter') onConfirm(name.trim())
    if (e.key === 'Escape') onCancel()
  }

  return (
    <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50">
      <div className="bg-zinc-800 rounded-xl p-6 w-80 shadow-2xl">
        <h2 className="text-white font-semibold mb-4">New Session</h2>
        <p className="text-zinc-400 text-sm mb-3">
          Optional name suffix — appended after the timestamp.
        </p>
        <input
          autoFocus
          value={name}
          onChange={(e) => setName(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="e.g. soundcheck"
          className="w-full bg-zinc-700 text-white rounded px-3 py-2 text-sm outline-none focus:ring-1 focus:ring-blue-500 mb-4"
        />
        <div className="flex gap-2 justify-end">
          <button
            onClick={onCancel}
            className="px-4 py-2 rounded text-sm text-zinc-300 hover:bg-zinc-700 transition-colors"
          >
            Cancel
          </button>
          <button
            onClick={() => onConfirm(name.trim())}
            className="px-4 py-2 rounded text-sm bg-blue-600 text-white hover:bg-blue-500 transition-colors"
          >
            Start
          </button>
        </div>
      </div>
    </div>
  )
}
