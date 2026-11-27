import { Navigate, NavLink, Outlet } from 'react-router'

import { useSession } from '../../auth/authContext'
import { mastersTabs } from '../../auth/permissions'

/** The masters area (R5): a title and one tab per list the role can open, then the list's page. */
export function MastersLayout() {
  const session = useSession()
  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-3">
        <h1 className="text-2xl font-semibold">Masters</h1>
        <nav aria-label="Masters" className="flex gap-4 overflow-x-auto border-b border-rule">
          {mastersTabs(session.user.role).map((tab) => (
            <NavLink
              key={tab.to}
              to={tab.to}
              className={({ isActive }) =>
                `-mb-px flex min-h-11 shrink-0 items-center border-b-2 text-sm ${
                  isActive ? 'border-primary font-semibold text-primary' : 'border-transparent text-ink hover:text-primary'
                }`
              }
            >
              {tab.label}
            </NavLink>
          ))}
        </nav>
      </div>
      <Outlet />
    </div>
  )
}

/** /masters opens the first list the role can see. */
export function MastersHome() {
  const session = useSession()
  return <Navigate to={mastersTabs(session.user.role)[0].to} replace />
}
