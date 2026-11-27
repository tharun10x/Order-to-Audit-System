import type { ReactNode } from 'react'
import { Link, Navigate, useLocation } from 'react-router'

import type { Role } from '../api/types'
import { useAuth } from './authContext'
import { roleHome } from './roleHome'

/**
 * R2 on the screen side (the backend enforces it anyway): signed out → login; signed in with another role →
 * a plain "not allowed" message with a way home, never a redirect loop.
 */
export function RequireRole({ roles, children }: { roles: Role[]; children: ReactNode }) {
  const { session } = useAuth()
  const location = useLocation()
  if (!session) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  if (!roles.includes(session.user.role)) {
    return <NotAllowed home={roleHome(session.user.role)} />
  }
  return children
}

export function NotAllowed({ home }: { home: string }) {
  return (
    <main className="mx-auto max-w-[1264px] px-8 py-8">
      <h1 className="text-2xl font-semibold">Not allowed</h1>
      <p className="mt-2 text-sm">Your role cannot open this page.</p>
      <Link to={home} className="mt-4 inline-block text-sm text-primary underline">
        Go to your start page
      </Link>
    </main>
  )
}
