import { label } from '../format'
import type { Category, Priority, TicketStatus } from '../types'

// Coloured pills; the colours come from CSS classes such as .status-open.
export function StatusBadge({ status }: { status: TicketStatus }) {
  return <span className={`pill status-${status.toLowerCase()}`}>{label(status)}</span>
}

export function PriorityBadge({ priority }: { priority: Priority }) {
  return <span className={`pill priority-${priority.toLowerCase()}`}>{label(priority)}</span>
}

export function CategoryBadge({ category }: { category: Category | null }) {
  return <span className="pill category">{category ? label(category) : 'Uncategorised'}</span>
}
