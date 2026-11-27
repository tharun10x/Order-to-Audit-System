import { NavLink, Outlet } from 'react-router'

import { useSession, useAuth } from '../../auth/authContext'

/** Phone first (UI guide section 7): one column up to 480 px, 52 px tap targets, no top-bar menu. */
export function SalesmanLayout() {
  const session = useSession()
  const { signOut } = useAuth()
  const tab = ({ isActive }: { isActive: boolean }) =>
    `flex min-h-13 flex-1 items-center justify-center border-b-2 text-base ${
      isActive ? 'border-primary font-semibold text-primary' : 'border-transparent text-ink'
    }`
  return (
    <div className="min-h-screen bg-surface">
      <header className="border-b border-border">
        <div className="mx-auto flex max-w-[480px] items-center justify-between px-4 pt-2">
          <span className="text-[13px] leading-[18px] text-muted">{session.user.fullName}</span>
          <button type="button" onClick={signOut} className="min-h-13 px-2 text-base text-primary">
            Sign out
          </button>
        </div>
        <nav aria-label="Main" className="mx-auto flex max-w-[480px] px-4">
          <NavLink to="/sales/new" className={tab}>
            New order
          </NavLink>
          <NavLink to="/sales/orders" className={tab}>
            My orders
          </NavLink>
          <NavLink to="/sales/my-sales" className={tab}>
            My sales
          </NavLink>
        </nav>
      </header>
      <main className="mx-auto max-w-[480px] px-4 py-5 text-base">
        <Outlet />
      </main>
    </div>
  )
}
