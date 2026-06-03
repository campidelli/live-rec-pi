import type { Session } from '../api/types'
import type { Status } from '../api/types'

export function useSessions(status: Status | null): Session[] {
  return status?.sessions ?? []
}
