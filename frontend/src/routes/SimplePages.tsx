import { Link, Navigate, useParams } from 'react-router'

import { useAuth } from '../auth/authContext'
import { roleHome } from '../auth/roleHome'

/** "/" sends each role to its start page. */
export function Home() {
  const { session } = useAuth()
  return <Navigate to={session ? roleHome(session.user.role) : '/login'} replace />
}

export function NotFound() {
  return (
    <main className="mx-auto max-w-[1264px] px-8 py-8">
      <h1 className="text-2xl font-semibold">Page not found</h1>
      <Link to="/" className="mt-4 inline-block text-sm text-primary underline">
        Go to your start page
      </Link>
    </main>
  )
}

/** Milestone 1 linked orders under /admin/orders; those links keep working. */
export function OldOrderLink() {
  const { orderId } = useParams()
  return <Navigate to={orderId ? `/orders/${orderId}` : '/orders'} replace />
}
