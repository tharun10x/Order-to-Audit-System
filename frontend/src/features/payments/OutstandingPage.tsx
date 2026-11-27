import { Link, useSearchParams } from 'react-router'

import { useCustomerOutstanding } from '../../api/billing'
import { useCustomers } from '../../api/queries'
import { AGEING_BUCKETS } from '../../api/types'
import { Field } from '../../components/Field'
import { PageHeader, Select } from '../../components/Layout'
import { EmptyState, ErrorBox } from '../../components/Messages'
import { Panel } from '../../components/Panel'
import { Table, Td, Th } from '../../components/Table'
import { formatDate, formatMoney, formatNumber } from '../../lib/format'
import { PAYMENT_STATUS } from '../../lib/labels'

/** R12.4: what one customer owes today, by age (days since invoice date), from the same calculation as the report. */
export function OutstandingPage() {
  const [params, setParams] = useSearchParams()
  const customerId = params.get('customer') ? Number(params.get('customer')) : null
  const customers = useCustomers()
  const outstanding = useCustomerOutstanding(customerId)

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Customer outstanding">
        <p className="mt-1 text-sm"><Link to="/payments" className="text-primary hover:underline">All payments</Link></p>
      </PageHeader>
      <Panel>
        <div className="max-w-sm">
          <Field label="Customer">
            {({ id }) => (
              <Select id={id} value={customerId ?? ''}
                onChange={(event) => setParams(event.target.value ? { customer: event.target.value } : {})}>
                <option value="">Pick a customer</option>
                {customers.data?.map((customer) => <option key={customer.id} value={customer.id}>{customer.name}</option>)}
              </Select>
            )}
          </Field>
        </div>
        {outstanding.error ? <div className="mt-4"><ErrorBox error={outstanding.error} /></div> : null}
        {outstanding.data ? (
          <div className="mt-6 flex flex-col gap-6">
            <p className="text-sm">
              <span className="text-muted">{`Outstanding on ${formatDate(outstanding.data.asOf)} `}</span>
              <span className="num text-lg font-semibold">{formatMoney(outstanding.data.totalOutstanding, { withSymbol: true })}</span>
            </p>
            <Table label="Ageing">
              <thead>
                <tr>
                  {AGEING_BUCKETS.map((bucket) => <Th key={bucket} numeric>{`${bucket} days (₹)`}</Th>)}
                </tr>
              </thead>
              <tbody>
                <tr>
                  {AGEING_BUCKETS.map((bucket) => (
                    <Td key={bucket} numeric>{formatMoney(outstanding.data.buckets[bucket] ?? 0)}</Td>
                  ))}
                </tr>
              </tbody>
            </Table>
            {outstanding.data.invoices.length === 0 ? (
              <EmptyState>Nothing is outstanding for this customer.</EmptyState>
            ) : (
              <Table label="Open invoices">
                <thead>
                  <tr>
                    <Th>Invoice</Th>
                    <Th>Date</Th>
                    <Th numeric>Total (₹)</Th>
                    <Th numeric>Paid (₹)</Th>
                    <Th numeric>Outstanding (₹)</Th>
                    <Th numeric>Days</Th>
                    <Th>Ageing</Th>
                    <Th>Status</Th>
                  </tr>
                </thead>
                <tbody>
                  {outstanding.data.invoices.map((invoice) => (
                    <tr key={invoice.invoiceId}>
                      <Td><Link to={`/invoices/${invoice.invoiceId}`} className="text-primary hover:underline">{invoice.invoiceNo}</Link></Td>
                      <Td>{formatDate(invoice.invoiceDate)}</Td>
                      <Td numeric>{formatMoney(invoice.total)}</Td>
                      <Td numeric>{formatMoney(invoice.paid)}</Td>
                      <Td numeric>{formatMoney(invoice.outstanding)}</Td>
                      <Td numeric>{formatNumber(invoice.days)}</Td>
                      <Td>{`${invoice.ageingBucket} days`}</Td>
                      <Td>{PAYMENT_STATUS[invoice.paymentStatus]}</Td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            )}
          </div>
        ) : null}
      </Panel>
    </div>
  )
}
