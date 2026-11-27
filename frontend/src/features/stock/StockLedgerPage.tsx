import type { ReactNode } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'

import { useItems, useLedger } from '../../api/operations'
import type { LedgerRow } from '../../api/types'
import { useSession } from '../../auth/authContext'
import { Field, TextInput } from '../../components/Field'
import { PageHeader, Select } from '../../components/Layout'
import { EmptyState, ErrorBox } from '../../components/Messages'
import { Panel } from '../../components/Panel'
import { Table, Td, Th } from '../../components/Table'
import { firstOfMonth, istDate, lastOfMonth } from '../../lib/dates'
import { formatDate, formatMoney, formatNumber, formatRate } from '../../lib/format'
import { MOVEMENT } from '../../lib/labels'

/**
 * R6.2, R6.4: every movement of one item with the running balance, each linked to the document that caused it.
 * The balance includes everything before the start date (the backend works it out).
 */
export function StockLedgerPage() {
  const { itemCode = '' } = useParams()
  const navigate = useNavigate()
  const { businessDate } = useSession()
  const [params, setParams] = useSearchParams()
  const from = params.get('from') ?? firstOfMonth(businessDate)
  const to = params.get('to') ?? lastOfMonth(businessDate)
  const items = useItems()
  const ledger = useLedger(itemCode, from, to)
  const item = items.data?.find((candidate) => candidate.code === itemCode)

  function setDate(key: 'from' | 'to', value: string) {
    const next = new URLSearchParams(params)
    next.set(key, value)
    setParams(next)
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title={item ? `Stock ledger: ${item.name}` : 'Stock ledger'}>
        <p className="mt-1 text-sm"><Link to="/stock" className="text-primary hover:underline">All stock</Link></p>
      </PageHeader>
      <Panel>
        <div className="mb-4 grid max-w-2xl grid-cols-1 gap-4 sm:grid-cols-3">
          <Field label="Item">
            {({ id }) => (
              <Select id={id} value={itemCode}
                onChange={(event) => navigate(`/stock/ledger/${event.target.value}?${params.toString()}`)}>
                {items.data?.map((option) => <option key={option.code} value={option.code}>{option.name}</option>)}
              </Select>
            )}
          </Field>
          <Field label="From">
            {({ id }) => <TextInput id={id} type="date" value={from} onChange={(event) => setDate('from', event.target.value)} />}
          </Field>
          <Field label="To">
            {({ id }) => <TextInput id={id} type="date" value={to} onChange={(event) => setDate('to', event.target.value)} />}
          </Field>
        </div>
        {ledger.error ? <ErrorBox error={ledger.error} /> : null}
        {ledger.data && ledger.data.length === 0 ? <EmptyState>No movements of this item in the period.</EmptyState> : null}
        {ledger.data && ledger.data.length > 0 && item ? (
          <Table label="Stock ledger">
            <thead>
              <tr>
                <Th>Date</Th>
                <Th>Movement</Th>
                <Th>Document</Th>
                <Th numeric>{`In (${item.baseUnit})`}</Th>
                <Th numeric>{`Out (${item.baseUnit})`}</Th>
                <Th numeric>Unit cost (₹)</Th>
                <Th numeric>Value (₹)</Th>
                <Th numeric>{`Balance (${item.baseUnit})`}</Th>
                <Th numeric>Balance value (₹)</Th>
              </tr>
            </thead>
            <tbody>
              {ledger.data.map((row) => (
                <tr key={row.id}>
                  <Td className="whitespace-nowrap">{formatDate(istDate(row.movementDate))}</Td>
                  <Td>{MOVEMENT[row.movementType]}</Td>
                  <Td>{documentLink(row)}</Td>
                  <Td numeric>{row.qty > 0 ? formatNumber(row.qty) : ''}</Td>
                  <Td numeric>{row.qty < 0 ? formatNumber(-row.qty) : ''}</Td>
                  <Td numeric>{formatRate(row.unitCost)}</Td>
                  <Td numeric>{formatMoney(row.value)}</Td>
                  <Td numeric className="font-semibold">{formatNumber(row.runningQty)}</Td>
                  <Td numeric>{formatMoney(row.runningValue)}</Td>
                </tr>
              ))}
            </tbody>
          </Table>
        ) : null}
      </Panel>
    </div>
  )
}

/** The source document as a link where there is a page for it: a run, or the invoice of a dispatch or credit note. */
function documentLink(row: LedgerRow): ReactNode {
  if (row.sourceType === 'PRODUCTION_RUN' && row.sourceId !== null) {
    return <Link to={`/production/runs/${row.sourceId}`} className="text-primary hover:underline">{row.documentNo}</Link>
  }
  if (row.invoiceId !== null) {
    return <Link to={`/invoices/${row.invoiceId}`} className="text-primary hover:underline">{row.documentNo}</Link>
  }
  if (row.sourceType === 'PURCHASE') {
    return <Link to="/purchases" className="text-primary hover:underline">{row.documentNo}</Link>
  }
  return row.documentNo
}
