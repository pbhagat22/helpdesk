import type { Problem } from './types'

// The token is kept in localStorage so a page reload doesn't log the user out.
// Trade-off: any script running on the page can read localStorage, so the app must
// never render untrusted HTML (React escapes text by default, which protects us).
const TOKEN_KEY = 'helpdesk.token'

export const getToken = () => localStorage.getItem(TOKEN_KEY)
export const setToken = (token: string) => localStorage.setItem(TOKEN_KEY, token)
export const clearToken = () => localStorage.removeItem(TOKEN_KEY)

// An error response from the API, carrying the ProblemDetail body.
export class ApiError extends Error {
  readonly status: number
  readonly problem: Problem | null

  constructor(status: number, problem: Problem | null) {
    super(problem?.detail ?? `Request failed (${status})`)
    this.status = status
    this.problem = problem
  }
}

// Called when the API rejects our token (expired, or the server restarted with a new
// key). The auth context sets this to log the user out.
let onUnauthorized: () => void = () => {}
export const setUnauthorizedHandler = (handler: () => void) => {
  onUnauthorized = handler
}

// Every API call goes through here: adds the JSON headers and the bearer token, and
// turns error responses into ApiError.
export async function api<T>(path: string, options: { method?: string; body?: unknown } = {}): Promise<T> {
  const headers: Record<string, string> = {}
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  const token = getToken()
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  const response = await fetch(`/api${path}`, {
    method: options.method ?? 'GET',
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  })

  if (response.status === 401 && token) {
    clearToken()
    onUnauthorized()
  }
  if (!response.ok) {
    const problem = (await response.json().catch(() => null)) as Problem | null
    throw new ApiError(response.status, problem)
  }
  return (await response.json()) as T
}
