import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { api, clearToken, getToken, setToken, setUnauthorizedHandler } from './api'
import type { TokenResponse, User } from './types'

interface AuthState {
  // undefined = still checking a saved token, null = logged out
  user: User | null | undefined
  login: (email: string, password: string) => Promise<void>
  register: (name: string, email: string, password: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthState | null>(null)

// Holds the logged-in user for the whole app. Any component can read it with useAuth().
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null | undefined>(getToken() ? undefined : null)

  const logout = useCallback(() => {
    clearToken()
    setUser(null)
  }, [])

  // On startup, a token saved by an earlier visit is checked by asking who it belongs to.
  useEffect(() => {
    setUnauthorizedHandler(logout)
    if (getToken()) {
      api<User>('/users/me')
        .then(setUser)
        .catch(logout)
    }
  }, [logout])

  const login = useCallback(async (email: string, password: string) => {
    const token = await api<TokenResponse>('/auth/login', { method: 'POST', body: { email, password } })
    setToken(token.accessToken)
    setUser(await api<User>('/users/me'))
  }, [])

  const register = useCallback(
    async (name: string, email: string, password: string) => {
      await api<User>('/auth/register', { method: 'POST', body: { name, email, password } })
      await login(email, password)
    },
    [login],
  )

  return <AuthContext.Provider value={{ user, login, register, logout }}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const auth = useContext(AuthContext)
  if (!auth) {
    throw new Error('useAuth must be used inside <AuthProvider>')
  }
  return auth
}
