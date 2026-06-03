import type { Channel, Mixer, OutputFormat, RecordingSettings, Session, Status } from './types'

const BASE = '/api'

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : {},
    body: body ? JSON.stringify(body) : undefined,
  })
  if (!res.ok) {
    const err = await res.json().catch(() => ({ detail: res.statusText }))
    throw new Error(err.detail ?? res.statusText)
  }
  return res.json() as Promise<T>
}

export const api = {
  getStatus: () => request<Status>('GET', '/status'),

  getMixers: () => request<{ mixers: Mixer[] }>('GET', '/mixers'),
  loadMixer: (id: string) => request<{ status: string }>('POST', `/mixers/${id}/load`),
  discoverMixer: (id: string) => request<{ host: string }>('POST', `/mixers/${id}/discover`),
  getChannels: (mixerId: string, host?: string) => {
    const q = host ? `?host=${encodeURIComponent(host)}` : ''
    return request<{ channels: Channel[] }>('GET', `/mixers/${mixerId}/channels${q}`)
  },
  setChannel: (mixerId: string, index: number, channelId: string) =>
    request<{ channels: Channel[] }>('PATCH', `/mixers/${mixerId}/channels/${index}`, {
      channel_id: channelId,
    }),

  getSessions: () =>
    request<Status>('GET', '/status').then((s) => s.sessions as Session[]),

  startRecording: () => request<{ status: string; session: string }>('POST', '/recordings/start'),
  stopRecording: () => request<{ status: string }>('POST', '/recordings/stop'),

  play: (session: string, filename: string, offset = 0) =>
    request<{ status: string }>('POST', '/playback/play', { session, filename, offset }),
  playbackStop: () => request<{ status: string }>('POST', '/playback/stop'),
  pause: () => request<{ status: string }>('POST', '/playback/pause'),
  resume: () => request<{ status: string }>('POST', '/playback/resume'),
  seek: (seconds: number) => request<{ status: string }>('POST', '/playback/seek', { seconds }),

  getSettings: () => request<{ settings: RecordingSettings }>('GET', '/settings'),
  updateSettings: (patch: Partial<RecordingSettings>) =>
    request<{ settings: RecordingSettings }>('PATCH', '/settings', patch),

  getFormats: () => request<{ formats: OutputFormat[] }>('GET', '/formats'),
}
