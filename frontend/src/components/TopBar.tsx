import { NavLink } from 'react-router'

import { useAuth } from '../auth/authContext'
import { NAV } from '../auth/permissions'

/**
 * UI guide section 6: 56 px, white, bottom border. App name, the pages this role can open (R2), user name and
 * Sign out. On a narrow screen the page links scroll sideways instead of wrapping.
 */
export function TopBar() {
  const { session, signOut } = useAuth()
  if (!session) {
    return null
  }
  return (
    <header className="border-b border-border bg-surface">
      <div className="mx-auto flex h-14 max-w-[1264px] items-center gap-6 px-4 sm:px-8">
        <span className="shrink-0 font-semibold">Order-to-Audit</span>
        <nav aria-label="Main" className="flex h-full min-w-0 gap-4 overflow-x-auto">
          {NAV[session.user.role].map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                `flex h-full shrink-0 items-center border-b-2 text-sm ${
                  isActive ? 'border-primary font-semibold text-primary' : 'border-transparent text-ink hover:text-primary'
                }`
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="ml-auto flex shrink-0 items-center gap-4 text-sm">
          <span className="hidden text-muted md:inline">{session.user.fullName}</span>
          <button type="button" onClick={signOut} className="min-h-11 px-2 text-primary hover:underline">
            Sign out
          </button>
        </div>
      </div>
    </header>
  )
}
