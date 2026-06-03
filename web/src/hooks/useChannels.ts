import { useState, useCallback } from 'react'
import { api } from '../api/client'

export function useChannels(mixerId: string | null) {
  const [selected, setSelected] = useState<Set<number>>(new Set())

  const toggle = useCallback((index: number) => {
    setSelected((prev) => {
      const next = new Set(prev)
      next.has(index) ? next.delete(index) : next.add(index)
      return next
    })
  }, [])

  const toggleAll = useCallback((indices: number[]) => {
    setSelected((prev) => {
      const allSelected = indices.every((i) => prev.has(i))
      return allSelected ? new Set() : new Set(indices)
    })
  }, [])

  const rename = useCallback(
    async (index: number, channelId: string) => {
      if (!mixerId) return
      await api.setChannel(mixerId, index, channelId)
    },
    [mixerId],
  )

  return { selected, toggle, toggleAll, rename }
}
