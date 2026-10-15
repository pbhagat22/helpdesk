import { createBrowserRouter, Navigate, Outlet, RouterProvider } from 'react-router'
import { useAuth } from './auth'
import { Layout } from './components/Layout'
import { LoginPage } from './pages/LoginPage'
import { NewTicketPage } from './pages/NewTicketPage'
import { RegisterPage } from './pages/RegisterPage'
import { TicketDetailPage } from './pages/TicketDetailPage'
import { TicketListPage } from './pages/TicketListPage'

// Pages under this route need a logged-in user; everyone else is sent to /login.
function RequireAuth() {
  const { user } = useAuth()
  if (user === undefined) {
    return <p className="centered">Loading…</p>
  }
  return user ? <Outlet /> : <Navigate to="/login" replace />
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
        ],
      },
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
])

export function App() {
  return <RouterProvider router={router} />
}
