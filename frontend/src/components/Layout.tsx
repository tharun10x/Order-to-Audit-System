import type { ReactNode, SelectHTMLAttributes } from 'react'
import { Link, type LinkProps } from 'react-router'

/** Page title with an optional action on the right (one primary action per screen area). */
export function PageHeader({ title, action, children }: { title: string; action?: ReactNode; children?: ReactNode }) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="text-2xl font-semibold">{title}</h1>
        {children}
      </div>
      {action}
    </div>
  )
}

/** A link that looks like a button: for "Record payment" and the like, which open another page. */
export function LinkButton({ variant = 'secondary', className = '', ...rest }: LinkProps & { variant?: 'primary' | 'secondary' }) {
  const look =
    variant === 'primary'
      ? 'bg-primary text-surface border-primary hover:bg-primary-hover'
      : 'bg-surface text-ink border-border hover:border-ink'
  return (
    <Link
      {...rest}
      className={`inline-flex min-h-11 items-center rounded-control border px-4 text-sm font-medium ${look} ${className}`}
    />
  )
}

/** Label-value pairs in a grid, for the facts at the top of a document. */
export function Facts({ items }: { items: [string, ReactNode][] }) {
  return (
    <dl className="grid grid-cols-[repeat(auto-fit,minmax(160px,1fr))] gap-4 text-sm">
      {items.map(([label, value]) => (
        <div key={label}>
          <dt className="text-[13px] leading-[18px] text-muted">{label}</dt>
          <dd className="mt-1">{value}</dd>
        </div>
      ))}
    </dl>
  )
}

/** A select in the guide's look: 44 px, 1 px border, 4 px radius. */
export function Select({ className = '', ...rest }: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select
      {...rest}
      className={`min-h-11 w-full rounded-control border border-border bg-surface px-3 text-sm text-ink ${className}`}
    />
  )
}

/** Status as a coloured word (never colour alone). */
export function StatusWord({ look }: { look: { label: string; className: string } }) {
  return <span className={`font-medium ${look.className}`}>{look.label}</span>
}
