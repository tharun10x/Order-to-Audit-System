import { NavLink } from 'react-router'

import type { Role } from '../api/types'
import { useAuth } from '../auth/authContext'

/** UI guide section 6: 56 px, white, bottom border. App name, the role's pages, user name and Sign out. */
const NAV: Record<Role, { to: string; label: string }[]> = {
  ADMIN: [{ to: '/admin/orders', label: 'Orders' }],
  PRODUCTION: [{ to: '/production/runs', label: 'Production runs' }],
  AUDITOR: [{ to: '/reports', label: 'Reports' }],
  OWNER: [{ to: '/reports', label: 'Reports' }],
  SALESMAN: [],
}

export function TopBar() {
  const { session, signOut } = useAuth()
  if (!session) {
    return null
  }
  return (
    <header className="border-b border-border bg-surface">
      <div className="mx-auto flex h-14 max-w-[1264px] items-center gap-6 px-8">
        <span className="font-semibold">Order-to-Audit</span>
        <nav aria-label="Main" className="flex h-full gap-4">
          {NAV[session.user.role].map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                `flex h-full items-center border-b-2 text-sm ${
                  isActive ? 'border-primary font-semibold text-primary' : 'border-transparent text-ink hover:text-primary'
                }`
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="ml-auto flex items-center gap-4 text-sm">
          <span className="text-muted">{session.user.fullName}</span>
          <button type="button" onClick={signOut} className="min-h-11 px-2 text-primary hover:underline">
            Sign out
          </button>
        </div>
      </div>
    </header>
  )
}
