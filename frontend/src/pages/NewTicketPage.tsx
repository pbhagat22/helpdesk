import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { api } from '../api'
import { ErrorMessage } from '../components/ErrorMessage'
import { label } from '../format'
import { PRIORITIES, type Priority, type Ticket } from '../types'

export function NewTicketPage() {
  const navigate = useNavigate()
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [priority, setPriority] = useState<Priority>('MEDIUM')
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      // No reporterId: the backend takes the reporter from the login token.
      const ticket = await api<Ticket>('/tickets', { method: 'POST', body: { title, description, priority } })
      navigate(`/tickets/${ticket.id}`)
    } catch (e) {
      setError(e)
      setBusy(false)
    }
  }

  return (
    <section className="narrow">
      <p>
        <Link to="/">← Back to tickets</Link>
      </p>
      <h1>New ticket</h1>
      <form onSubmit={handleSubmit}>
        <label>
          Title
          <input value={title} onChange={(e) => setTitle(e.target.value)} required maxLength={255} autoFocus />
        </label>
        <label>
          Description
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            required
            maxLength={4000}
            rows={6}
          />
        </label>
        <label>
          Priority
          <select value={priority} onChange={(e) => setPriority(e.target.value as Priority)}>
            {PRIORITIES.map((p) => (
              <option key={p} value={p}>
                {label(p)}
              </option>
            ))}
          </select>
        </label>
        <ErrorMessage error={error} />
        <button type="submit" disabled={busy}>
          {busy ? 'Submitting…' : 'Submit ticket'}
        </button>
      </form>
    </section>
  )
}
