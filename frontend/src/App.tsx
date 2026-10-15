import { createBrowserRouter, Navigate, Outlet, RouterProvider } from 'react-router'
import { useAuth } from './auth'
import { Layout } from './components/Layout'
import { HomePage } from './pages/HomePage'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'

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
        children: [{ path: '/', element: <HomePage /> }],
      },
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
])

export function App() {
  return <RouterProvider router={router} />
}
