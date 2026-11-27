import type { ReactNode } from 'react'

/** UI guide section 6: white, 1 px border, 6 px radius, 24 px padding, no shadow, never nested. */
export function Panel({ title, children, className = '' }: { title?: string; children: ReactNode; className?: string }) {
  return (
    <section className={`rounded-panel border border-border bg-surface p-6 ${className}`}>
      {title ? <h2 className="mb-4 text-lg leading-[26px] font-semibold">{title}</h2> : null}
      {children}
    </section>
  )
}
