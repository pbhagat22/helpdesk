// TypeScript versions of the backend's JSON (the Java *Response records).

export type Role = 'CUSTOMER' | 'AGENT' | 'ADMIN'

export interface User {
  id: number
  email: string
  name: string
  role: Role
  createdAt: string
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
