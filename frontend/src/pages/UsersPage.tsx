import { useState, type FormEvent } from 'react'
import { api } from '../api'
import { useAuth } from '../auth'
import { ErrorMessage } from '../components/ErrorMessage'
import { formatDate, label } from '../format'
import { ROLES, type Role, type User } from '../types'
import { useApi } from '../useApi'

// Admins only (the route is guarded in App.tsx, and the backend enforces it regardless).
export function UsersPage() {
  const { data: users, error, reload } = useApi<User[]>('/users')

  return (
    <section>
      <h1>Users</h1>
      <CreateUserForm onCreated={reload} />

      <h2>All users</h2>
      <p className="hint">
        A role change applies the next time that person logs in (their current login keeps the old role for up to an
        hour).
      </p>
      <ErrorMessage error={error} />
      {users === undefined && !error && <p>Loading…</p>}
      {users && (
        <table className="tickets">
          <thead>
            <tr>
              <th>Name</th>
              <th>Email</th>
              <th>Role</th>
              <th>Joined</th>
            </tr>
          </thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.id}>
                <td>{u.name}</td>
                <td>{u.email}</td>
                <td>
                  <RoleSelect user={u} onChanged={reload} />
                </td>
                <td className="hint">{formatDate(u.createdAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}

// Saves as soon as a new role is picked. Your own row is locked: the backend refuses to let
// admins change their own role, so there's always at least one admin.
function RoleSelect({ user, onChanged }: { user: User; onChanged: () => void }) {
  const { user: me } = useAuth()
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)
  const isMe = me?.id === user.id

  async function change(role: Role) {
    setBusy(true)
    setError(null)
    try {
      await api<User>(`/users/${user.id}`, { method: 'PATCH', body: { role } })
      onChanged()
    } catch (e) {
      setError(e)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="role-select">
      <select
        value={user.role}
        disabled={isMe || busy}
        onChange={(e) => change(e.target.value as Role)}
        aria-label={`Role of ${user.name}`}
      >
        {ROLES.map((r) => (
          <option key={r} value={r}>
            {label(r)}
          </option>
        ))}
      </select>
      {isMe && <span className="hint">(you)</span>}
      <ErrorMessage error={error} />
    </div>
  )
}

function CreateUserForm({ onCreated }: { onCreated: () => void }) {
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState<Role>('AGENT')
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)
  const [created, setCreated] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    setCreated(null)
    try {
      const user = await api<User>('/users', { method: 'POST', body: { name, email, password, role } })
      setCreated(`Created ${user.name} (${label(user.role)}). Share the password with them securely.`)
      setName('')
      setEmail('')
      setPassword('')
      onCreated()
    } catch (e) {
      setError(e)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="card create-user" onSubmit={handleSubmit}>
      <h2>Add a user</h2>
      <div className="create-user-fields">
        <label>
          Name
          <input value={name} onChange={(e) => setName(e.target.value)} required />
        </label>
        <label>
          Email
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </label>
        <label>
          Password <span className="hint">(8–72 characters)</span>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            minLength={8}
            maxLength={72}
            autoComplete="new-password"
          />
        </label>
        <label>
          Role
          <select value={role} onChange={(e) => setRole(e.target.value as Role)}>
            {ROLES.map((r) => (
              <option key={r} value={r}>
                {label(r)}
              </option>
            ))}
          </select>
        </label>
      </div>
      <ErrorMessage error={error} />
      {created && <p className="success">{created}</p>}
      <div>
        <button type="submit" disabled={busy}>
          {busy ? 'Creating…' : 'Create user'}
        </button>
      </div>
    </form>
  )
}
