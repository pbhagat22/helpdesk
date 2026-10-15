import { Link, useSearchParams } from 'react-router'
import { useAuth } from '../auth'
import { PriorityBadge, StatusBadge } from '../components/Badges'
import { ErrorMessage } from '../components/ErrorMessage'
import { formatDate, isStaff, label } from '../format'
import { TICKET_STATUSES, type Ticket, type User } from '../types'
import { useApi } from '../useApi'

// Customers see "My tickets" (the backend only returns their own). Staff see the whole
// queue and can filter it. Filters live in the URL (?status=OPEN&assignee=me), so a
// filtered view survives a reload and works with the browser's back button.
export function TicketListPage() {
  const { user } = useAuth()
  const staff = isStaff(user?.role)
  const [searchParams, setSearchParams] = useSearchParams()
  const status = searchParams.get('status') ?? ''
  const assignee = searchParams.get('assignee') ?? ''

  const query = new URLSearchParams()
  if (status) query.set('status', status)
  const assigneeId = assignee === 'me' ? String(user?.id) : assignee
  if (assigneeId) query.set('assigneeId', assigneeId)
  const { data: tickets, error } = useApi<Ticket[]>(`/tickets${query.size ? `?${query}` : ''}`)

  function setFilter(name: string, value: string) {
    const next = new URLSearchParams(searchParams)
    if (value) {
      next.set(name, value)
    } else {
      next.delete(name)
    }
    setSearchParams(next)
  }

  return (
    <section>
      <div className="page-header">
        <h1>{staff ? 'Ticket queue' : 'My tickets'}</h1>
        <Link to="/tickets/new" className="button">
          New ticket
        </Link>
      </div>

      <div className="filters">
        <label>
          Status
          <select value={status} onChange={(e) => setFilter('status', e.target.value)}>
            <option value="">All</option>
            {TICKET_STATUSES.map((s) => (
              <option key={s} value={s}>
                {label(s)}
              </option>
            ))}
          </select>
        </label>
        {staff && <AssigneeFilter value={assignee} onChange={(value) => setFilter('assignee', value)} />}
      </div>

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
    </section>
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
