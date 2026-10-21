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

export type Category = 'ACCOUNT' | 'BILLING' | 'BUG' | 'HOW_TO' | 'OTHER'
export const CATEGORIES: Category[] = ['ACCOUNT', 'BILLING', 'BUG', 'HOW_TO', 'OTHER']

export interface Ticket {
  id: number
  title: string
  description: string
  status: TicketStatus
  priority: Priority
  category: Category | null // null until staff categorise it
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

export type ActivityType =
  | 'CREATED'
  | 'STATUS_CHANGED'
  | 'PRIORITY_CHANGED'
  | 'CATEGORY_CHANGED'
  | 'ASSIGNED'
  | 'COMMENTED'

// One entry in a ticket's history (built by the backend from Kafka events)
export interface Activity {
  id: number
  type: ActivityType
  actor: User | null
  details: Record<string, string>
  occurredAt: string
}

// The AI's summary of a ticket and a draft reply (staff only, POST /tickets/{id}/assist),
// with the knowledge base articles the reply is based on
export interface AssistResponse {
  summary: string
  suggestedReply: string
  sources: { id: number; title: string }[]
}

// The AI's suggested priority and category for a new ticket (staff only, GET /tickets/{id}/triage)
export interface Triage {
  priority: Priority
  category: Category
  reason: string
  model: string
  createdAt: string
}

// The agent's answer to a staff question, and the tool calls it made (POST /agent/ask)
export interface AgentResponse {
  answer: string
  steps: { tool: string; input: string; error: string | null }[]
  modelCalls: number
  inputTokens: number
  outputTokens: number
}

// A knowledge base article. chunkCount 0 = not searchable (embedding failed)
export interface Article {
  id: number
  title: string
  body: string
  author: User
  createdAt: string
  updatedAt: string
  chunkCount: number
}

// A semantic search hit: the article's best-matching passage and how close in meaning it is (0-1)
export interface ArticleSearchResult {
  id: number
  title: string
  snippet: string
  similarity: number
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
