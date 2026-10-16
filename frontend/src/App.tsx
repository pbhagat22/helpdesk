import { createBrowserRouter, Navigate, Outlet, RouterProvider } from 'react-router'
import { useAuth } from './auth'
import { Layout } from './components/Layout'
import { LoginPage } from './pages/LoginPage'
import { NewTicketPage } from './pages/NewTicketPage'
import { RegisterPage } from './pages/RegisterPage'
import { TicketDetailPage } from './pages/TicketDetailPage'
import { TicketListPage } from './pages/TicketListPage'
import { UsersPage } from './pages/UsersPage'

// Pages under this route need a logged-in user; everyone else is sent to /login.
function RequireAuth() {
  const { user } = useAuth()
  if (user === undefined) {
    return <p className="centered">Loading…</p>
  }
  return user ? <Outlet /> : <Navigate to="/login" replace />
}

// Admin-only pages: anyone else is sent back to the ticket list. This only hides the page;
// the real protection is the backend's 403.
function RequireAdmin() {
  const { user } = useAuth()
  return user?.role === 'ADMIN' ? <Outlet /> : <Navigate to="/" replace />
}

// Client-side routing: the browser URL picks the page without a server round trip.
const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/register', element: <RegisterPage /> },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <Layout />,
        children: [
          { path: '/', element: <TicketListPage /> },
          { path: '/tickets/new', element: <NewTicketPage /> },
          { path: '/tickets/:id', element: <TicketDetailPage /> },
          { element: <RequireAdmin />, children: [{ path: '/users', element: <UsersPage /> }] },
        ],
      },
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
])

export function App() {
  return <RouterProvider router={router} />
}
