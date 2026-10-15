import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { ApiError } from '../api'
import { useAuth } from '../auth'
import { ErrorMessage } from '../components/ErrorMessage'

export function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  if (user) {
    return <Navigate to="/" replace />
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault() // stop the browser's own form submission (a full page reload)
    setBusy(true)
    setError(null)
    try {
      await login(email, password)
      navigate('/')
    } catch (e) {
      // The API deliberately says only "Authentication required"; say it more helpfully.
      setError(
        e instanceof ApiError && e.status === 401
          ? new ApiError(401, { status: 401, title: 'Unauthorized', detail: 'Wrong email or password' })
          : e,
      )
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="auth-card">
      <h1>Log in</h1>
      <form onSubmit={handleSubmit}>
        <label>
          Email
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoFocus />
        </label>
        <label>
          Password
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        </label>
        <ErrorMessage error={error} />
        <button type="submit" disabled={busy}>
          {busy ? 'Logging in…' : 'Log in'}
        </button>
      </form>
      <p>
        No account yet? <Link to="/register">Sign up</Link>
      </p>
    </div>
  )
}
