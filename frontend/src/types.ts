// TypeScript versions of the backend's JSON (the Java *Response records).

export type Role = 'CUSTOMER' | 'AGENT' | 'ADMIN'
export const ROLES: Role[] = ['CUSTOMER', 'AGENT', 'ADMIN']

export interface User {
  id: number
  email: string
  name: string
  role: Role
  createdAt: string
}

export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED'
export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'

export const TICKET_STATUSES: TicketStatus[] = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED']
export const PRIORITIES: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'URGENT']

export interface Ticket {
  id: number
  title: string
  description: string
  status: TicketStatus
  priority: Priority
  reporter: User
  assignee: User | null
  createdAt: string
  updatedAt: string
}

export interface Comment {
  id: number
  ticketId: number
  author: User
  body: string
  createdAt: string
}

// A search hit: the ticket (from Postgres) plus snippets of matching text with marked words
export interface SearchResult {
  ticket: Ticket
  highlights: string[]
}

export type ActivityType = 'CREATED' | 'STATUS_CHANGED' | 'PRIORITY_CHANGED' | 'ASSIGNED' | 'COMMENTED'

// One entry in a ticket's history (built by the backend from Kafka events)
export interface Activity {
  id: number
  type: ActivityType
  actor: User | null
  details: Record<string, string>
  occurredAt: string
}

export interface TokenResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
}

// RFC 9457 problem detail, as produced by GlobalExceptionHandler
export interface Problem {
  status: number
  title: string
  detail?: string
  errors?: Record<string, string>
}
