import type { Client, Level } from '@/api/client'

// The API sends enum codes (openapi.yaml); the UI shows these names.

const CLIENTS: Record<Client, string> = {
  DKB: 'DKB',
  DEKA: 'Deka',
  VV: 'VV',
  DBIS: 'DBIS',
  UNION: 'Union',
}

const LEVELS: Record<Level, string> = {
  JUNIOR: 'Junior',
  EXPERT: 'Expert',
  SENIOR: 'Senior',
  ARCHITECT: 'Architect',
  SENIOR_ARCHITECT: 'Senior architect',
}

export const clientLabel = (client: Client) => CLIENTS[client]
export const levelLabel = (level: Level) => LEVELS[level]

/** "Ana Silva" → "AS", "Ana" → "A" */
export function initials(name: string): string {
  const parts = name.trim().split(/\s+/)
  const first = parts[0]?.[0] ?? ''
  const last = parts.length > 1 ? (parts[parts.length - 1]?.[0] ?? '') : ''
  return (first + last).toUpperCase()
}
