import { Outlet } from 'react-router'

import { TopBar } from '../components/TopBar'

/** Admin, production, auditor and owner: top bar, content up to 1200 px wide, left-aligned (UI guide section 7). */
export function DesktopLayout() {
  return (
    <div className="min-h-screen">
      <TopBar />
      <main className="mx-auto max-w-[1264px] px-4 py-7 sm:px-8">
        <Outlet />
      </main>
    </div>
  )
}
