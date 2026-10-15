import { useAuth } from '../auth'

// Placeholder until the ticket screens arrive in the next step.
export function HomePage() {
  const { user } = useAuth()
  return (
    <section>
      <h1>Welcome, {user?.name}</h1>
      <p>
        You're logged in as <strong>{user?.email}</strong> with the role <strong>{user?.role}</strong>.
      </p>
      <p className="hint">Ticket screens are coming in the next step.</p>
    </section>
  )
}
