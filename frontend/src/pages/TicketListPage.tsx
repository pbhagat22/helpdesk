import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { useAuth } from '../auth'
import { PriorityBadge, StatusBadge } from '../components/Badges'
import { ErrorMessage } from '../components/ErrorMessage'
import { Highlighted, stripMarks } from '../components/Highlighted'
import { formatDate, isStaff, label } from '../format'
import { TICKET_STATUSES, type SearchResult, type Ticket, type User } from '../types'
import { useApi } from '../useApi'

// Customers see "My tickets" (the backend only returns their own). Staff see the whole
// queue and can filter it. Filters and the search text live in the URL
// (?status=OPEN&assignee=me, ?q=printer), so a view survives a reload and works with the
// browser's back button. While there's search text, ranked search results replace the list.
export function TicketListPage() {
  const { user } = useAuth()
  const staff = isStaff(user?.role)
  const [searchParams, setSearchParams] = useSearchParams()
  const status = searchParams.get('status') ?? ''
  const assignee = searchParams.get('assignee') ?? ''
  const query = searchParams.get('q') ?? ''

  // replace: update the URL without adding a history entry (used while typing, so Back
  // doesn't step through "p", "pr", "pri", ...)
  function setParam(name: string, value: string, replace = false) {
    const next = new URLSearchParams(searchParams)
    if (value) {
      next.set(name, value)
    } else {
      next.delete(name)
    }
    setSearchParams(next, { replace })
  }

  return (
    <section>
      <div className="page-header">
        <h1>{staff ? 'Ticket queue' : 'My tickets'}</h1>
        <Link to="/tickets/new" className="button">
          New ticket
        </Link>
      </div>

      <SearchBox value={query} onChange={(text) => setParam('q', text, true)} />

      {query.trim() ? (
        <SearchResults query={query.trim()} staff={staff} />
      ) : (
        <>
          <div className="filters">
            <label>
              Status
              <select value={status} onChange={(e) => setParam('status', e.target.value)}>
                <option value="">All</option>
                {TICKET_STATUSES.map((s) => (
                  <option key={s} value={s}>
                    {label(s)}
                  </option>
                ))}
              </select>
            </label>
            {staff && <AssigneeFilter value={assignee} onChange={(value) => setParam('assignee', value)} />}
          </div>
          <TicketTable
            status={status}
            assigneeId={assignee === 'me' ? String(user?.id) : assignee}
            staff={staff}
          />
        </>
      )}
    </section>
  )
}

// The text box keeps its own state so typing stays instant; the URL (and so the search
// request) only follows once typing pauses for 300 ms. That's "debouncing": without it,
// typing "printer" would send seven requests.
function SearchBox({ value, onChange }: { value: string; onChange: (text: string) => void }) {
  const [text, setText] = useState(value)

  // If the URL changes from outside (Back button, clicking "Helpdesk"), show its value.
  const [lastValue, setLastValue] = useState(value)
  if (value !== lastValue) {
    setLastValue(value)
    setText(value)
  }

  useEffect(() => {
    if (text === value) {
      return
    }
    const timer = setTimeout(() => onChange(text), 300)
    return () => clearTimeout(timer) // a new keystroke cancels the pending update
  }, [text, value, onChange])

  return (
    <div className="search">
      <input
        type="search"
        placeholder="Search tickets (title, description, comments)…"
        aria-label="Search tickets"
        value={text}
        onChange={(e) => setText(e.target.value)}
      />
    </div>
  )
}

function SearchResults({ query, staff }: { query: string; staff: boolean }) {
  const { data: results, error } = useApi<SearchResult[]>(`/tickets/search?q=${encodeURIComponent(query)}`)

  return (
    <>
      <ErrorMessage error={error} />
      {results === undefined && !error && <p>Searching…</p>}
      {results?.length === 0 && <p className="hint">No tickets match “{query}”.</p>}
      <ul className="search-results">
        {results?.map(({ ticket, highlights }) => {
          // Highlights mix title, description and comment snippets. A snippet that is the
          // whole title is used for the title itself; the rest are shown underneath.
          const titleHighlight = highlights.find((h) => stripMarks(h) === ticket.title)
          const snippets = highlights.filter((h) => h !== titleHighlight).slice(0, 2)
          return (
            <li key={ticket.id} className="card">
              <div className="result-head">
                <Link to={`/tickets/${ticket.id}`}>
                  #{ticket.id} <Highlighted text={titleHighlight ?? ticket.title} />
                </Link>
                <div className="pills">
                  <StatusBadge status={ticket.status} />
                  <PriorityBadge priority={ticket.priority} />
                </div>
              </div>
              {snippets.map((snippet, index) => (
                <p key={index} className="snippet">
                  …<Highlighted text={snippet} />…
                </p>
              ))}
              <p className="hint">
                {staff && <>Reported by {ticket.reporter.name} · </>}
                {ticket.assignee ? `Assigned to ${ticket.assignee.name}` : 'Unassigned'} · Updated{' '}
                {formatDate(ticket.updatedAt)}
              </p>
            </li>
          )
        })}
      </ul>
    </>
  )
}

function TicketTable({ status, assigneeId, staff }: { status: string; assigneeId: string; staff: boolean }) {
  const params = new URLSearchParams()
  if (status) params.set('status', status)
  if (assigneeId) params.set('assigneeId', assigneeId)
  const { data: tickets, error } = useApi<Ticket[]>(`/tickets${params.size ? `?${params}` : ''}`)

  return (
    <>
      <ErrorMessage error={error} />
      {tickets === undefined && !error && <p>Loading…</p>}
      {tickets?.length === 0 && <p className="hint">No tickets match.</p>}
      {tickets && tickets.length > 0 && (
        <table className="tickets">
          <thead>
            <tr>
              <th>#</th>
              <th>Title</th>
              <th>Status</th>
              <th>Priority</th>
              {staff && <th>Reporter</th>}
              <th>Assignee</th>
              <th>Updated</th>
            </tr>
          </thead>
          <tbody>
            {tickets.map((ticket) => (
              <tr key={ticket.id}>
                <td>{ticket.id}</td>
                <td>
                  <Link to={`/tickets/${ticket.id}`}>{ticket.title}</Link>
                </td>
                <td>
                  <StatusBadge status={ticket.status} />
                </td>
                <td>
                  <PriorityBadge priority={ticket.priority} />
                </td>
                {staff && <td>{ticket.reporter.name}</td>}
                <td>{ticket.assignee?.name ?? <span className="hint">Unassigned</span>}</td>
                <td className="hint">{formatDate(ticket.updatedAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  )
}

// Its own component so the /users request only happens for staff (customers get 403).
function AssigneeFilter({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  const { data: users } = useApi<User[]>('/users')
  const agents = users?.filter((u) => u.role !== 'CUSTOMER') ?? []
  return (
    <label>
      Assignee
      <select value={value} onChange={(e) => onChange(e.target.value)}>
        <option value="">Anyone</option>
        <option value="me">Assigned to me</option>
        {agents.map((agent) => (
          <option key={agent.id} value={String(agent.id)}>
            {agent.name}
          </option>
        ))}
      </select>
    </label>
  )
}
