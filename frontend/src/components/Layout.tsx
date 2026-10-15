import { Link, Outlet } from 'react-router'
import { useAuth } from '../auth'

// The frame around every logged-in page: header with the user and a logout button.
export function Layout() {
  const { user, logout } = useAuth()
  return (
    <>
      <header className="topbar">
        <Link to="/" className="brand">
          Helpdesk
        </Link>
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
