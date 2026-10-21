import { Link, NavLink, Outlet } from 'react-router'
import { useAuth } from '../auth'
import { isStaff } from '../format'

// The frame around every logged-in page: header with navigation, the user and a logout button.
export function Layout() {
  const { user, logout } = useAuth()
  return (
    <>
      <header className="topbar">
        <nav className="topbar-nav">
          <Link to="/" className="brand">
            Helpdesk
          </Link>
          {/* NavLink adds class="active" when its page is open; "end" so / isn't active on /users */}
          <NavLink to="/" end>
            Tickets
          </NavLink>
          {isStaff(user?.role) && <NavLink to="/ask">Ask AI</NavLink>}
          {user?.role === 'ADMIN' && <NavLink to="/users">Users</NavLink>}
        </nav>
        <div className="topbar-user">
          <span>
            {user?.name} <span className="badge">{user?.role}</span>
          </span>
          <button className="secondary" onClick={logout}>
            Log out
          </button>
        </div>
      </header>
      <main className="content">
        <Outlet />
      </main>
    </>
  )
}
