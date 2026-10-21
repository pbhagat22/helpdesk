import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { api, ApiError } from '../api'
import { useAuth } from '../auth'
import { PriorityBadge, StatusBadge } from '../components/Badges'
import { ErrorMessage } from '../components/ErrorMessage'
import { formatDate, isStaff, label } from '../format'
import {
  PRIORITIES,
  TICKET_STATUSES,
  type Activity,
  type AssistResponse,
  type Comment,
  type Priority,
  type Ticket,
  type TicketStatus,
  type User,
} from '../types'
import { useApi } from '../useApi'

export function TicketDetailPage() {
  const { id } = useParams()
  const { user } = useAuth()
  const { data: ticket, error, reload: reloadTicket } = useApi<Ticket>(`/tickets/${id}`)
  const { data: comments, reload: reloadComments } = useApi<Comment[]>(`/tickets/${id}/comments`)
  const { data: history, reload: reloadHistory } = useApi<Activity[]>(`/tickets/${id}/activity`)
  // The comment being written lives here, so the AI assistant can fill it with its draft
  const [commentBody, setCommentBody] = useState('')

  // The history is built from Kafka events in the background, so right after a change it may
  // not include it yet ("eventually consistent"). Reload now, and once more a moment later.
  function refreshHistory() {
    reloadHistory()
    setTimeout(reloadHistory, 1500)
  }

  // Customers get 404 for tickets that aren't theirs (the backend hides that they exist).
  if (error instanceof ApiError && error.status === 404) {
    return <NotFound />
  }
  if (error) {
    return <ErrorMessage error={error} />
  }
  if (!ticket) {
    return <p>Loading…</p>
  }

  return (
    <section>
      <p>
        <Link to="/">← Back to tickets</Link>
      </p>
      <div className="page-header">
        <h1>
          #{ticket.id} {ticket.title}
        </h1>
        <div className="pills">
          <StatusBadge status={ticket.status} />
          <PriorityBadge priority={ticket.priority} />
        </div>
      </div>
      <p className="hint">
        Reported by {ticket.reporter.name} on {formatDate(ticket.createdAt)} · Assigned to{' '}
        {ticket.assignee?.name ?? 'nobody yet'}
      </p>
      <div className="card description">{ticket.description}</div>

      {/* key: when the ticket changes (after a save), start the form fresh from the new values */}
      {isStaff(user?.role) && (
        <StaffControls
          key={ticket.updatedAt}
          ticket={ticket}
          onSaved={() => {
            reloadTicket()
            refreshHistory()
          }}
        />
      )}

      {isStaff(user?.role) && <AiAssistant ticketId={ticket.id} onUseReply={setCommentBody} />}

      <h2>Conversation</h2>
      {comments?.length === 0 && <p className="hint">No comments yet.</p>}
      <ul className="comments">
        {comments?.map((comment) => (
          <li key={comment.id} className="card">
            <div className="comment-meta">
              <strong>{comment.author.name}</strong>
              {comment.author.role !== 'CUSTOMER' && <span className="badge">Support</span>}
              <span className="hint">{formatDate(comment.createdAt)}</span>
            </div>
            <p>{comment.body}</p>
          </li>
        ))}
      </ul>
      <CommentForm
        ticketId={ticket.id}
        body={commentBody}
        setBody={setCommentBody}
        onAdded={() => {
          reloadComments()
          refreshHistory()
        }}
      />

      <h2>History</h2>
      {history?.length === 0 && <p className="hint">Nothing yet. The history updates a moment after each change.</p>}
      <ol className="history">
        {history?.map((entry) => (
          <li key={entry.id}>
            <span>{describe(entry)}</span> <span className="hint">· {formatDate(entry.occurredAt)}</span>
          </li>
        ))}
      </ol>
    </section>
  )
}

// One readable sentence per history entry
function describe(entry: Activity): string {
  const who = entry.actor?.name ?? 'Someone'
  const { details } = entry
  switch (entry.type) {
    case 'CREATED':
      return `${who} opened the ticket`
    case 'STATUS_CHANGED':
      return `${who} changed the status from ${label(details.from)} to ${label(details.to)}`
    case 'PRIORITY_CHANGED':
      return `${who} changed the priority from ${label(details.from)} to ${label(details.to)}`
    case 'ASSIGNED':
      return details.assigneeId === String(entry.actor?.id)
        ? `${who} assigned it to themselves`
        : `${who} assigned it to ${details.assigneeName}`
    case 'COMMENTED':
      return `${who} commented`
  }
}

// Status, priority and assignee: staff only (the backend returns 403 to customers anyway).
function StaffControls({ ticket, onSaved }: { ticket: Ticket; onSaved: () => void }) {
  const { data: users } = useApi<User[]>('/users')
  const [status, setStatus] = useState<TicketStatus>(ticket.status)
  const [priority, setPriority] = useState<Priority>(ticket.priority)
  const [assigneeId, setAssigneeId] = useState(ticket.assignee ? String(ticket.assignee.id) : '')
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  const agents = users?.filter((u) => u.role !== 'CUSTOMER') ?? []
  const changed =
    status !== ticket.status ||
    priority !== ticket.priority ||
    assigneeId !== (ticket.assignee ? String(ticket.assignee.id) : '')

  async function handleSave(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      // PATCH: send only what changed; the backend leaves missing fields alone.
      await api<Ticket>(`/tickets/${ticket.id}`, {
        method: 'PATCH',
        body: {
          status: status !== ticket.status ? status : undefined,
          priority: priority !== ticket.priority ? priority : undefined,
          assigneeId: assigneeId && assigneeId !== String(ticket.assignee?.id) ? Number(assigneeId) : undefined,
        },
      })
      onSaved()
    } catch (e) {
      setError(e)
      setBusy(false)
    }
  }

  return (
    <form className="card staff-controls" onSubmit={handleSave}>
      <label>
        Status
        <select value={status} onChange={(e) => setStatus(e.target.value as TicketStatus)}>
          {TICKET_STATUSES.map((s) => (
            <option key={s} value={s}>
              {label(s)}
            </option>
          ))}
        </select>
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
      <label>
        Assignee
        <select value={assigneeId} onChange={(e) => setAssigneeId(e.target.value)}>
          {/* The API has no "unassign", so "Unassigned" is only offered while nobody is assigned */}
          {!ticket.assignee && <option value="">Unassigned</option>}
          {agents.map((agent) => (
            <option key={agent.id} value={String(agent.id)}>
              {agent.name}
            </option>
          ))}
        </select>
      </label>
      <button type="submit" disabled={!changed || busy}>
        {busy ? 'Saving…' : 'Save'}
      </button>
      <ErrorMessage error={error} />
    </form>
  )
}

// Staff only: asks Claude (through the backend) for a summary and a draft reply.
// The draft is only copied into the comment box; an agent reads and edits it before sending.
function AiAssistant({ ticketId, onUseReply }: { ticketId: number; onUseReply: (reply: string) => void }) {
  const [result, setResult] = useState<AssistResponse | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  async function handleAsk() {
    setBusy(true)
    setError(null)
    try {
      setResult(await api<AssistResponse>(`/tickets/${ticketId}/assist`, { method: 'POST' }))
    } catch (e) {
      setError(e)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="card ai-assistant">
      <div className="ai-assistant-head">
        <strong>AI assistant</strong>
        <button type="button" className="secondary" onClick={handleAsk} disabled={busy}>
          {busy ? 'Thinking…' : result ? 'Try again' : 'Summarise and draft a reply'}
        </button>
      </div>
      <ErrorMessage error={error} />
      {result && (
        <>
          <h3>Summary</h3>
          <p>{result.summary}</p>
          <h3>Suggested reply</h3>
          <p className="suggested-reply">{result.suggestedReply}</p>
          <button type="button" onClick={() => onUseReply(result.suggestedReply)}>
            Use this reply
          </button>
          <p className="hint">AI-generated. Check it before sending.</p>
        </>
      )}
    </div>
  )
}

function CommentForm({
  ticketId,
  body,
  setBody,
  onAdded,
}: {
  ticketId: number
  body: string
  setBody: (body: string) => void
  onAdded: () => void
}) {
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api<Comment>(`/tickets/${ticketId}/comments`, { method: 'POST', body: { body } })
      setBody('')
      onAdded()
    } catch (e) {
      setError(e)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={handleSubmit}>
      <label>
        Add a comment
        <textarea value={body} onChange={(e) => setBody(e.target.value)} required maxLength={4000} rows={3} />
      </label>
      <ErrorMessage error={error} />
      <div>
        <button type="submit" disabled={busy || !body.trim()}>
          {busy ? 'Sending…' : 'Send'}
        </button>
      </div>
    </form>
  )
}

function NotFound() {
  return (
    <section>
      <h1>Ticket not found</h1>
      <p>
        It doesn't exist, or it isn't yours. <Link to="/">Back to tickets</Link>
      </p>
    </section>
  )
}
