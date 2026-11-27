import { Link, useSearchParams } from 'react-router'

import { useInvoices } from '../../api/billing'
import type { InvoiceStatus } from '../../api/types'
import { useSession } from '../../auth/authContext'
import { Field, TextInput } from '../../components/Field'
import { PageHeader, Select, StatusWord } from '../../components/Layout'
import { EmptyState, ErrorBox } from '../../components/Messages'
import { Panel } from '../../components/Panel'
import { Table, Td, Th } from '../../components/Table'
import { firstOfMonth, lastOfMonth } from '../../lib/dates'
import { formatDate, formatMoney } from '../../lib/format'
import { INVOICE_STATUS, PAYMENT_STATUS } from '../../lib/labels'

/** R10: GST invoices, by status and date range (this business month unless changed). Filters live in the URL. */
export function InvoicesPage() {
  const { businessDate } = useSession()
  const [params, setParams] = useSearchParams()
  const status = (params.get('status') as InvoiceStatus | null) ?? undefined
  const from = params.get('from') ?? firstOfMonth(businessDate)
  const to = params.get('to') ?? lastOfMonth(businessDate)
  const invoices = useInvoices({ status, from, to })

  function set(key: string, value: string) {
    const next = new URLSearchParams(params)
    if (value) {
      next.set(key, value)
    } else {
      next.delete(key)
    }
    setParams(next)
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Invoices" />
      <Panel>
        <div className="mb-4 grid max-w-2xl grid-cols-1 gap-4 sm:grid-cols-3">
          <Field label="Status">
            {({ id }) => (
              <Select id={id} value={status ?? ''} onChange={(event) => set('status', event.target.value)}>
                <option value="">All invoices</option>
                <option value="POSTED">Posted</option>
                <option value="CANCELLED">Cancelled</option>
              </Select>
            )}
          </Field>
          <Field label="From">
            {({ id }) => <TextInput id={id} type="date" value={from} onChange={(event) => set('from', event.target.value)} />}
          </Field>
          <Field label="To">
            {({ id }) => <TextInput id={id} type="date" value={to} onChange={(event) => set('to', event.target.value)} />}
          </Field>
        </div>
        {invoices.error ? <ErrorBox error={invoices.error} /> : null}
        {invoices.data && invoices.data.length === 0 ? (
          <EmptyState>No invoices in this period. Invoices are created from approved orders.</EmptyState>
        ) : null}
        {invoices.data && invoices.data.length > 0 ? (
          <Table label="Invoices">
            <thead>
              <tr>
                <Th>Invoice no</Th>
                <Th>Date</Th>
                <Th>Customer</Th>
                <Th>Place of supply</Th>
                <Th numeric>Taxable (₹)</Th>
                <Th numeric>GST (₹)</Th>
                <Th numeric>Total (₹)</Th>
                <Th>Dispatched</Th>
                <Th numeric>Outstanding (₹)</Th>
                <Th>Status</Th>
              </tr>
            </thead>
            <tbody>
              {invoices.data.map((invoice) => (
                <tr key={invoice.id}>
                  <Td className="whitespace-nowrap">
                    <Link to={`/invoices/${invoice.id}`} className="font-medium text-primary hover:underline">
                      {invoice.invoiceNo}
                    </Link>
                  </Td>
                  <Td className="whitespace-nowrap">{formatDate(invoice.invoiceDate)}</Td>
                  <Td>{invoice.buyer.name}</Td>
                  <Td className="whitespace-nowrap">{`${invoice.placeOfSupply} · ${invoice.supplyType === 'INTRA' ? 'Intra-state' : 'Inter-state'}`}</Td>
                  <Td numeric>{formatMoney(invoice.taxableValue)}</Td>
                  <Td numeric>{formatMoney(invoice.taxTotal)}</Td>
                  <Td numeric>{formatMoney(invoice.total)}</Td>
                  <Td className="whitespace-nowrap">{invoice.dispatchNo ?? <span className="text-muted">Not dispatched</span>}</Td>
                  <Td numeric>
                    {invoice.outstanding === null ? '' : formatMoney(invoice.outstanding)}
                    {invoice.paymentStatus ? (
                      <span className="block text-[13px] text-muted">{PAYMENT_STATUS[invoice.paymentStatus]}</span>
                    ) : null}
                  </Td>
                  <Td>
                    <StatusWord look={INVOICE_STATUS[invoice.status]} />
                  </Td>
                </tr>
              ))}
            </tbody>
          </Table>
        ) : null}
      </Panel>
    </div>
  )
}
