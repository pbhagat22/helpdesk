import type { Role } from './types'

// "IN_PROGRESS" → "In progress"
export function label(value: string): string {
  const words = value.toLowerCase().replace(/_/g, ' ')
  return words.charAt(0).toUpperCase() + words.slice(1)
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
}

export const isStaff = (role: Role | undefined) => role === 'AGENT' || role === 'ADMIN'
