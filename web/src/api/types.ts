export interface Channel {
  index: number
  id: string
  mixer_name?: string
  level?: number // 0.0–1.0, from WS meters (future)
}

export interface SessionFile {
  name: string
  size_bytes: number
}

export interface Session {
  session: string
  files: SessionFile[]
}

export interface Mixer {
  id: string
  name: string
  active: boolean
}

export interface RecordingSettings {
  output_path: string
  session_template: string
  format: string
}

export interface OutputFormat {
  id: string
  description: string
}

export type DaemonStatus = 'recording' | 'idle'

export interface Status {
  status: DaemonStatus
  connected: boolean
  device: string | null
  device_model: string | null
  mixer_id: string | null
  channels: Channel[]
  recording_session: string | null
  recording_files: string[]
  record_time: number
  playing: boolean
  paused: boolean
  play_session: string | null
  play_file: string | null
  position: number
  duration: number
  sessions: Session[]
  storage_path: string
  storage_free_bytes: number
  storage_total_bytes: number
  error?: string
}
