import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'

import { useCustomerOutstanding, useRecordPayment } from '../../api/billing'
import { useCustomers } from '../../api/queries'
import type { Payment, PaymentMode } from '../../api/types'
import { useSession } from '../../auth/authContext'
import { Button } from '../../components/Button'
import { Field, TextInput } from '../../components/Field'
import { PageHeader, Select } from '../../components/Layout'
import { EmptyState, ErrorBox, SuccessLine } from '../../components/Messages'
import { Panel } from '../../components/Panel'
import { Table, Td, Th } from '../../components/Table'
import { paiseOf, toPaise } from '../../lib/decimal'
import { formatDate, formatMoney } from '../../lib/format'
import { PAYMENT_MODE } from '../../lib/labels'

/**
 * R12.1, R12.2, R12.6: money from one customer, split over their open invoices. The parts must add up exactly to
 * the amount (no advances), and each part must not exceed what is still due. Amounts are sent as typed; the
 * check here only compares whole paise, so nothing is rounded in the browser.
 */
export function RecordPaymentPage() {
  const [params, setParams] = useSearchParams()
  const customerId = params.get('customer') ? Number(params.get('customer')) : null
  const preselectedInvoice = params.get('invoice') ? Number(params.get('invoice')) : null
  const customers = useCustomers()
  const [recorded, setRecorded] = useState<Payment | null>(null)

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Record payment">
        <p className="mt-1 text-sm"><Link to="/payments" className="text-primary hover:underline">All payments</Link></p>
      </PageHeader>
      {recorded ? <RecordedLine payment={recorded} /> : null}
      <Panel>
        <div className="max-w-sm">
          <Field label="Customer">
            {({ id }) => (
              <Select
                id={id}
                value={customerId ?? ''}
                onChange={(event) => {
                  setRecorded(null)
                  setParams(event.target.value ? { customer: event.target.value } : {})
                }}
              >
                <option value="">Pick a customer</option>
                {customers.data?.map((customer) => (
                  <option key={customer.id} value={customer.id}>{customer.name}</option>
                ))}
              </Select>
            )}
          </Field>
        </div>
        {customerId ? (
          <PaymentForm
            key={`${customerId}-${preselectedInvoice}`}
            customerId={customerId}
            preselectedInvoice={preselectedInvoice}
            onRecorded={setRecorded}
          />
        ) : null}
      </Panel>
    </div>
  )
}

function RecordedLine({ payment }: { payment: Payment }) {
  const settled = payment.allocations
    .map((a) => `${a.invoiceNo} now has ${formatMoney(a.outstanding ?? 0, { withSymbol: true })} outstanding`)
    .join('; ')
  return <SuccessLine>{`Payment of ${formatMoney(payment.amount, { withSymbol: true })} recorded. ${settled}.`}</SuccessLine>
}

const MODES: PaymentMode[] = ['CASH', 'UPI', 'BANK', 'CHEQUE']

function PaymentForm({ customerId, preselectedInvoice, onRecorded }: {
  customerId: number
  preselectedInvoice: number | null
  onRecorded: (payment: Payment) => void
}) {
  const { businessDate } = useSession()
  const outstanding = useCustomerOutstanding(customerId)
  const record = useRecordPayment()
  const [amount, setAmount] = useState('')
  const [mode, setMode] = useState<PaymentMode>('UPI')
  const [reference, setReference] = useState('')
  const [receivedOn, setReceivedOn] = useState(businessDate)
  /** Typed allocation per invoice id. The prefilled invoice follows the amount until its own field is edited. */
  const [allocations, setAllocations] = useState<Record<number, string>>({})
  const [followsAmount, setFollowsAmount] = useState(true)

  if (outstanding.error) {
    return <div className="mt-4"><ErrorBox error={outstanding.error} /></div>
  }
  if (!outstanding.data) {
    return <p className="mt-4 text-sm text-muted">Loading open invoices…</p>
  }
  const invoices = outstanding.data.invoices
  if (invoices.length === 0) {
    return <div className="mt-4"><EmptyState>This customer owes nothing. Payments are recorded against open invoices.</EmptyState></div>
  }
  const prefilled = invoices.find((invoice) => invoice.invoiceId === preselectedInvoice)?.invoiceId
    ?? (invoices.length === 1 ? invoices[0].invoiceId : null)

  const allocationText = (invoiceId: number) =>
    followsAmount && invoiceId === prefilled && allocations[invoiceId] === undefined ? amount : (allocations[invoiceId] ?? '')

  // Whole paise only: "allocated = amount" is a comparison, never a sum that is sent
  const amountPaise = toPaise(amount)
  const parts = invoices.map((invoice) => ({ invoice, text: allocationText(invoice.invoiceId) }))
  const used = parts.filter((part) => part.text.trim() !== '')
  const badPart = used.find((part) => toPaise(part.text) === null || toPaise(part.text) === 0)
  const overPart = used.find((part) => (toPaise(part.text) ?? 0) > paiseOf(part.invoice.outstanding))
  const allocatedPaise = used.reduce((sum, part) => sum + (toPaise(part.text) ?? 0), 0)
  const balanced = amountPaise !== null && amountPaise > 0 && allocatedPaise === amountPaise
  const ready = balanced && !badPart && !overPart && used.length > 0 && receivedOn !== ''

  function submit() {
    record.mutate(
      {
        customerId,
        receivedOn,
        amount: amount.trim(),
        mode,
        reference: reference.trim() || null,
        allocations: used.map((part) => ({ invoiceId: part.invoice.invoiceId, amount: part.text.trim() })),
      },
      { onSuccess: onRecorded },
    )
  }

  return (
    <div className="mt-6 flex flex-col gap-6">
      <div className="grid max-w-2xl grid-cols-1 gap-4 sm:grid-cols-2">
        <Field label="Amount received (₹)" error={amount && amountPaise === null ? 'Enter an amount like 4000 or 4000.50.' : undefined}>
          {({ id, describedBy, invalid }) => (
            <TextInput id={id} inputMode="decimal" value={amount} aria-describedby={describedBy} aria-invalid={invalid}
              className="num" onChange={(event) => setAmount(event.target.value)} />
          )}
        </Field>
        <Field label="Received on">
          {({ id }) => <TextInput id={id} type="date" value={receivedOn} onChange={(event) => setReceivedOn(event.target.value)} />}
        </Field>
        <Field label="Mode">
          {({ id }) => (
            <Select id={id} value={mode} onChange={(event) => setMode(event.target.value as PaymentMode)}>
              {MODES.map((value) => <option key={value} value={value}>{PAYMENT_MODE[value]}</option>)}
            </Select>
          )}
        </Field>
        <Field label="Reference (optional)" hint="UPI reference, cheque number or bank reference.">
          {({ id, describedBy }) => (
            <TextInput id={id} value={reference} maxLength={50} aria-describedby={describedBy}
              onChange={(event) => setReference(event.target.value)} />
          )}
        </Field>
      </div>

      <div>
        <h2 className="mb-2 text-lg leading-[26px] font-semibold">Settle these invoices</h2>
        <Table label="Allocation">
          <thead>
            <tr>
              <Th>Invoice</Th>
              <Th>Date</Th>
              <Th numeric>Total (₹)</Th>
              <Th numeric>Outstanding (₹)</Th>
              <Th numeric>Allocate (₹)</Th>
            </tr>
          </thead>
          <tbody>
            {parts.map(({ invoice, text }) => {
              const over = text.trim() !== '' && (toPaise(text) ?? 0) > paiseOf(invoice.outstanding)
              return (
                <tr key={invoice.invoiceId}>
                  <Td>{invoice.invoiceNo}</Td>
                  <Td>{formatDate(invoice.invoiceDate)}</Td>
                  <Td numeric>{formatMoney(invoice.total)}</Td>
                  <Td numeric>{formatMoney(invoice.outstanding)}</Td>
                  <Td numeric>
                    <TextInput
                      aria-label={`Allocate to ${invoice.invoiceNo}`}
                      inputMode="decimal"
                      value={text}
                      aria-invalid={over}
                      className="num max-w-36 text-right"
                      onChange={(event) => {
                        if (invoice.invoiceId === prefilled) {
                          setFollowsAmount(false)
                        }
                        setAllocations((current) => ({ ...current, [invoice.invoiceId]: event.target.value }))
                      }}
                    />
                    {over ? <p className="mt-1 text-[13px] text-danger">More than is outstanding.</p> : null}
                  </Td>
                </tr>
              )
            })}
          </tbody>
        </Table>
        <p
          role="status"
          className={`num mt-3 text-sm font-semibold ${balanced || amountPaise === null ? 'text-ink' : 'text-danger'}`}
        >
          {`Allocated ${formatMoney(allocatedPaise / 100, { withSymbol: true })} of ${formatMoney((amountPaise ?? 0) / 100, { withSymbol: true })}`}
          {amountPaise !== null && !balanced ? '. They must be equal: advances are not recorded.' : ''}
        </p>
        {badPart ? <p className="text-sm text-danger">Allocations must be amounts above 0 with at most 2 decimals.</p> : null}
      </div>

      {record.error ? <ErrorBox error={record.error} /> : null}
      <div>
        <Button variant="primary" onClick={submit} disabled={!ready} loading={record.isPending}>
          Record payment
        </Button>
        {!ready ? (
          <p className="mt-2 text-[13px] leading-[18px] text-muted">
            Enter the amount and split it over the invoices; the button works once the allocations add up to the amount.
          </p>
        ) : null}
      </div>
    </div>
  )
}
