import { Download, Share2 } from 'lucide-react'
import { useState } from 'react'
import { Link, useParams } from 'react-router'

import { useInvoice } from '../../api/billing'
import type { Invoice, Party } from '../../api/types'
import { useSession } from '../../auth/authContext'
import { can } from '../../auth/permissions'
import { Button } from '../../components/Button'
import { Facts, LinkButton, PageHeader, StatusWord } from '../../components/Layout'
import { ErrorBox, SuccessLine } from '../../components/Messages'
import { Panel } from '../../components/Panel'
import { Table, Td, Th } from '../../components/Table'
import { formatDate, formatMoney, formatNumber } from '../../lib/format'
import { INVOICE_STATUS, PAYMENT_MODE, PAYMENT_RECORD_STATUS, PAYMENT_STATUS } from '../../lib/labels'
import { saveFile } from '../../lib/saveFile'
import { CancelInvoicePanel } from './CancelInvoicePanel'
import { DispatchPanel } from './DispatchPanel'
import { fetchInvoicePdf, shareFile, whatsAppLink } from './invoiceFile'

/**
 * R10.5: an invoice as it was raised (the seller and buyer copied on it, not today's master data), with what
 * happened to it since: dispatch, payments, cancellation. Only the admin sees the actions.
 */
export function InvoiceDetailPage() {
  const { invoiceId } = useParams()
  const invoice = useInvoice(Number(invoiceId))
  const session = useSession()
  const [notice, setNotice] = useState<string | null>(null)

  if (invoice.error) {
    return <ErrorBox error={invoice.error} />
  }
  if (!invoice.data) {
    return <p className="text-sm text-muted">Loading invoice…</p>
  }
  const data = invoice.data
  const manage = can.manageSales(session.user.role) && data.status === 'POSTED'

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title={`Invoice ${data.invoiceNo}`}>
        <p className="mt-1 text-sm text-muted">
          <Link to="/invoices" className="text-primary hover:underline">All invoices</Link>
        </p>
      </PageHeader>
      {notice ? <SuccessLine>{notice}</SuccessLine> : null}

      <Panel>
        <Facts
          items={[
            ['Invoice date', formatDate(data.invoiceDate)],
            ['Status', <StatusWord key="s" look={INVOICE_STATUS[data.status]} />],
            ['Order', <Link key="o" to={`/orders/${data.orderId}`} className="text-primary hover:underline">{data.orderNo}</Link>],
            ['Place of supply', `${data.placeOfSupply} · ${data.supplyType === 'INTRA' ? 'CGST + SGST' : 'IGST'}`],
            ...(data.creditNoteNo ? [['Credit note', data.creditNoteNo] as [string, string]] : []),
          ]}
        />
        <div className="mt-6 grid gap-6 md:grid-cols-2">
          <PartyBlock title="Seller" party={data.seller} />
          <PartyBlock title="Buyer" party={data.buyer} />
        </div>
        <FileActions invoice={data} />
      </Panel>

      <Panel title="Lines">
        <LinesTable invoice={data} />
        <InvoiceTotal invoice={data} />
      </Panel>

      <Panel title="Dispatch">
        {data.dispatchNo ? (
          <p className="text-sm">{`Dispatched as ${data.dispatchNo} on ${formatDate(data.dispatchDate!)}.`}</p>
        ) : manage ? (
          <DispatchPanel invoice={data} onDone={setNotice} />
        ) : (
          <p className="text-sm text-muted">Not dispatched.</p>
        )}
      </Panel>

      <PaymentsPanel invoice={data} canRecord={manage} />

      {manage ? <CancelInvoicePanel invoice={data} onDone={setNotice} /> : null}
    </div>
  )
}

function PartyBlock({ title, party }: { title: string; party: Party }) {
  return (
    <div className="text-sm">
      <p className="text-[13px] leading-[18px] text-muted">{title}</p>
      <p className="mt-1 font-semibold">{party.name}</p>
      {party.address ? <p>{party.address}</p> : null}
      <p>{party.gstin ? `GSTIN ${party.gstin}` : 'Unregistered'}</p>
      <p>{`State code ${party.stateCode}`}</p>
    </div>
  )
}

/** Download the PDF; share it on a phone, or download plus a WhatsApp link elsewhere. */
function FileActions({ invoice }: { invoice: Invoice }) {
  const [busy, setBusy] = useState<'download' | 'share' | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [offerWhatsApp, setOfferWhatsApp] = useState(false)

  async function run(kind: 'download' | 'share') {
    setBusy(kind)
    setError(null)
    try {
      const file = await fetchInvoicePdf(invoice)
      if (kind === 'share' && (await shareFile(file, invoice))) {
        return
      }
      saveFile(file)
      setOfferWhatsApp(kind === 'share')
    } catch (failure) {
      setError(failure)
    } finally {
      setBusy(null)
    }
  }

  return (
    <div className="mt-6 flex flex-col gap-3">
      <div className="flex flex-wrap gap-3">
        <Button onClick={() => void run('download')} loading={busy === 'download'}>
          <span className="inline-flex items-center gap-2"><Download size={16} aria-hidden="true" />Download PDF</span>
        </Button>
        <Button onClick={() => void run('share')} loading={busy === 'share'}>
          <span className="inline-flex items-center gap-2"><Share2 size={16} aria-hidden="true" />Share invoice</span>
        </Button>
      </div>
      {offerWhatsApp ? (
        <p className="text-sm">
          The PDF is downloaded. Attach it in WhatsApp, or{' '}
          <a href={whatsAppLink(invoice)} target="_blank" rel="noreferrer" className="text-primary underline">
            send the invoice details on WhatsApp
          </a>
          .
        </p>
      ) : null}
      <ErrorBox error={error} />
    </div>
  )
}

function LinesTable({ invoice }: { invoice: Invoice }) {
  const intra = invoice.supplyType === 'INTRA'
  return (
    <Table label="Invoice lines">
      <thead>
        <tr>
          <Th>Item</Th>
          <Th>HSN</Th>
          <Th numeric>Qty (pkt)</Th>
          <Th numeric>Rate (₹)</Th>
          <Th numeric>Taxable (₹)</Th>
          <Th numeric>GST %</Th>
          {intra ? <Th numeric>CGST (₹)</Th> : null}
          {intra ? <Th numeric>SGST (₹)</Th> : null}
          {intra ? null : <Th numeric>IGST (₹)</Th>}
        </tr>
      </thead>
      <tbody>
        {invoice.lines.map((line) => (
          <tr key={line.itemCode}>
            <Td>{line.itemName}</Td>
            <Td className="num">{line.hsnCode}</Td>
            <Td numeric>{formatNumber(line.qty)}</Td>
            <Td numeric>{formatMoney(line.rate)}</Td>
            <Td numeric>{formatMoney(line.taxableValue)}</Td>
            <Td numeric>{`${formatNumber(line.gstRate)}%`}</Td>
            {intra ? <Td numeric>{formatMoney(line.cgst)}</Td> : null}
            {intra ? <Td numeric>{formatMoney(line.sgst)}</Td> : null}
            {intra ? null : <Td numeric>{formatMoney(line.igst)}</Td>}
          </tr>
        ))}
      </tbody>
      <tfoot>
        <tr>
          <Td className="font-semibold" colSpan={4}>Total</Td>
          <Td numeric className="font-semibold">{formatMoney(invoice.taxableValue)}</Td>
          <Td />
          {intra ? <Td numeric className="font-semibold">{formatMoney(invoice.cgst)}</Td> : null}
          {intra ? <Td numeric className="font-semibold">{formatMoney(invoice.sgst)}</Td> : null}
          {intra ? null : <Td numeric className="font-semibold">{formatMoney(invoice.igst)}</Td>}
        </tr>
      </tfoot>
    </Table>
  )
}

/** Outside the table, so it stays in view on a phone while the lines scroll sideways. */
function InvoiceTotal({ invoice }: { invoice: Invoice }) {
  return (
    <p className="mt-4 flex justify-between gap-6 border-t border-rule pt-3 text-lg font-semibold">
      <span>Invoice total</span>
      <span className="num">{formatMoney(invoice.total, { withSymbol: true })}</span>
    </p>
  )
}

function PaymentsPanel({ invoice, canRecord }: { invoice: Invoice; canRecord: boolean }) {
  return (
    <Panel title="Payments">
      {invoice.outstanding !== null ? (
        <p className="mb-4 text-sm">
          <span className="text-muted">Outstanding </span>
          <span className="num text-lg font-semibold">{formatMoney(invoice.outstanding, { withSymbol: true })}</span>
          {invoice.paymentStatus ? <span className="text-muted">{` · ${PAYMENT_STATUS[invoice.paymentStatus]}`}</span> : null}
        </p>
      ) : (
        <p className="mb-4 text-sm text-muted">Nothing is receivable on a cancelled invoice.</p>
      )}
      {invoice.payments.length > 0 ? (
        <Table label="Payments against this invoice">
          <thead>
            <tr>
              <Th>Received on</Th>
              <Th>Mode</Th>
              <Th>Reference</Th>
              <Th numeric>Amount (₹)</Th>
              <Th>Status</Th>
            </tr>
          </thead>
          <tbody>
            {invoice.payments.map((payment) => (
              <tr key={payment.paymentId}>
                <Td>{formatDate(payment.receivedOn)}</Td>
                <Td>{PAYMENT_MODE[payment.mode]}</Td>
                <Td>{payment.reference ?? ''}</Td>
                <Td numeric>{formatMoney(payment.amount)}</Td>
                <Td><StatusWord look={PAYMENT_RECORD_STATUS[payment.status]} /></Td>
              </tr>
            ))}
          </tbody>
        </Table>
      ) : (
        <p className="text-sm text-muted">No payments yet.</p>
      )}
      {canRecord && invoice.outstanding !== null && invoice.outstanding > 0 ? (
        <div className="mt-4">
          <LinkButton variant="primary" to={`/payments/new?customer=${invoice.customerId}&invoice=${invoice.id}`}>
            Record payment
          </LinkButton>
        </div>
      ) : null}
    </Panel>
  )
}
