import { useState } from 'react'

import { useReport } from '../../api/reports'
import type { ReportCell, ReportTable } from '../../api/types'
import { useSession } from '../../auth/authContext'
import { Field, TextInput } from '../../components/Field'
import { EmptyState, ErrorBox } from '../../components/Messages'
import { firstOfMonth, lastOfMonth } from '../../lib/dates'
import { formatMoney, formatNumber } from '../../lib/format'

/**
 * Brief 4.1: the salesman's customer-wise sales. The server lists only the customers assigned to this salesman
 * (R2.2) with their orders, invoiced value and outstanding; the totals are the server's too. Phone first: one
 * block per customer instead of a wide table.
 */
export function MySalesPage() {
  const { businessDate } = useSession()
  const [from, setFrom] = useState(firstOfMonth(businessDate))
  const [to, setTo] = useState(lastOfMonth(businessDate))
  const error = !from || !to ? 'Pick both days.' : from > to ? 'The first day must not be after the last day.' : null
  const report = useReport('my-sales', { from, to }, !error)
  const section = report.data?.sections[0]

  return (
    <>
      <h1 className="mb-4 text-xl leading-7 font-semibold">My sales</h1>
      <div className="mb-5 grid grid-cols-2 gap-3">
        <Field label="From" error={error ?? undefined}>
          {({ id, describedBy }) => (
            <TextInput id={id} type="date" large aria-describedby={describedBy} value={from} onChange={(event) => setFrom(event.target.value)} />
          )}
        </Field>
        <Field label="To">
          {({ id }) => <TextInput id={id} type="date" large value={to} onChange={(event) => setTo(event.target.value)} />}
        </Field>
      </div>
      {report.error ? <ErrorBox error={report.error} /> : null}
      {!error && report.isPending ? <p className="text-base text-muted">Loading…</p> : null}
      {section ? <Customers section={section} /> : null}
    </>
  )
}

function Customers({ section }: { section: ReportTable }) {
  // Cells are read by column key, so a column added on the server cannot shift them
  const column = (key: string) => section.columns.findIndex((candidate) => candidate.key === key)
  const read = (row: ReportCell[], key: string) => row[column(key)]
  const money = (value: ReportCell) => formatMoney(Number(value ?? 0), { withSymbol: true })

  if (section.rows.length === 0) {
    return <EmptyState>No customers are assigned to you yet. Ask the admin to assign them.</EmptyState>
  }
  return (
    <>
      <ul aria-label="My customers">
        {section.rows.map((row) => (
          <li key={String(read(row, 'customerCode'))} className="border-b border-rule py-3">
            <p className="font-semibold">{String(read(row, 'customer'))}</p>
            <Figures orders={read(row, 'orders')} invoiced={money(read(row, 'invoiced'))} outstanding={money(read(row, 'outstanding'))} />
          </li>
        ))}
      </ul>
      {section.totals ? (
        <section aria-label="Total" className="mt-4">
          <p className="font-semibold">Total</p>
          <Figures orders={read(section.totals, 'orders')} invoiced={money(read(section.totals, 'invoiced'))}
            outstanding={money(read(section.totals, 'outstanding'))} bold />
        </section>
      ) : null}
    </>
  )
}

function Figures({ orders, invoiced, outstanding, bold = false }: { orders: ReportCell; invoiced: string; outstanding: string; bold?: boolean }) {
  const rows: [string, string][] = [
    ['Orders', formatNumber(Number(orders ?? 0))],
    ['Invoiced', invoiced],
    ['Outstanding', outstanding],
  ]
  return (
    <dl className="mt-1 grid grid-cols-3 gap-2">
      {rows.map(([label, value]) => (
        <div key={label}>
          <dt className="text-[13px] leading-[18px] text-muted">{label}</dt>
          <dd className={`num text-base ${bold ? 'font-semibold' : ''}`}>{value}</dd>
        </div>
      ))}
    </dl>
  )
}
