import { useState, type ReactNode } from 'react'

import { useOrder, useStockCheck } from '../../api/queries'
import type { Order } from '../../api/types'
import { useSession } from '../../auth/authContext'
import { can } from '../../auth/permissions'
import { ErrorBox, SuccessLine } from '../../components/Messages'
import { Panel } from '../../components/Panel'
import { StatusText } from '../../components/StatusText'
import { Table, Td, Th } from '../../components/Table'
import { formatDate, formatMoney, formatNumber } from '../../lib/format'
import { ApprovePanel } from './ApprovePanel'
import { CreateInvoicePanel } from './CreateInvoicePanel'
import { EditLinesPanel } from './EditLinesPanel'
import { RejectPanel } from './RejectPanel'
import { StockCheckTable } from './StockCheckTable'

/**
 * One order: its lines, then what can be done with it now. SUBMITTED: stock check, approve, edit, reject
 * (R9.3 to R9.5). APPROVED: create the invoice. Each action area is its own panel (no nested panels).
 * Only the admin sees the actions; auditor and owner read the order.
 */
export function OrderDetail({ orderId }: { orderId: number }) {
  const session = useSession()
  // Auditor and owner see the order read-only: no stock check (an admin tool) and no actions (R2.3)
  const manage = can.manageSales(session.user.role)
  const order = useOrder(orderId)
  const submitted = manage && order.data?.status === 'SUBMITTED'
  const stockCheck = useStockCheck(orderId, submitted)
  const [notice, setNotice] = useState<string | null>(null)

  if (order.error) {
    return <ErrorBox error={order.error} />
  }
  if (!order.data) {
    return <p className="text-sm text-muted">Loading order…</p>
  }
  return (
    <div className="flex flex-col gap-6">
      <OrderPanel order={order.data} notice={notice} />
      {submitted ? (
        <Panel title="Stock check">
          {stockCheck.error ? <ErrorBox error={stockCheck.error} /> : null}
          {stockCheck.data ? (
            <>
              <StockCheckTable
                stockCheck={stockCheck.data}
                names={Object.fromEntries(order.data.lines.map((line) => [line.itemCode, line.itemName]))}
              />
              <ApprovePanel order={order.data} stockCheck={stockCheck.data} onDone={setNotice} />
            </>
          ) : (
            <p className="text-sm text-muted">Checking stock…</p>
          )}
        </Panel>
      ) : null}
      {submitted ? <EditLinesPanel order={order.data} onDone={setNotice} /> : null}
      {submitted ? <RejectPanel order={order.data} onDone={setNotice} /> : null}
      {manage && order.data.status === 'APPROVED' ? <CreateInvoicePanel order={order.data} onDone={setNotice} /> : null}
    </div>
  )
}

function OrderPanel({ order, notice }: { order: Order; notice: string | null }) {
  const facts: [string, ReactNode][] = [
    ['Customer', order.customerName],
    ['Salesman', order.salesman],
    ['Order date', formatDate(order.orderDate)],
    ['Status', <StatusText key="status" status={order.status} />],
  ]
  if (order.approvedBy) {
    facts.push(['Approved by', order.approvedBy])
  }
  if (order.invoiceNo) {
    facts.push(['Invoice', order.invoiceNo])
  }
  if (order.status === 'REJECTED' && order.remarks) {
    facts.push(['Reason', order.remarks])
  }
  const anyApproved = order.lines.some((line) => line.approvedQty !== null)
  return (
    <Panel title={`Order ${order.orderNo}`}>
      {notice ? (
        <div className="mb-4">
          <SuccessLine>{notice}</SuccessLine>
        </div>
      ) : null}
      <dl className="mb-6 grid grid-cols-[repeat(auto-fit,minmax(160px,1fr))] gap-4 text-sm">
        {facts.map(([label, value]) => (
          <div key={label}>
            <dt className="text-[13px] leading-[18px] text-muted">{label}</dt>
            <dd className="mt-1">{value}</dd>
          </div>
        ))}
      </dl>
      <Table label="Order lines">
        <thead>
          <tr>
            <Th>Item</Th>
            <Th numeric>Ordered (pkt)</Th>
            {anyApproved ? <Th numeric>Approved (pkt)</Th> : null}
            <Th numeric>Rate (₹)</Th>
            <Th numeric>Value (₹)</Th>
          </tr>
        </thead>
        <tbody>
          {order.lines.map((line) => (
            <tr key={line.itemCode}>
              <Td>{line.itemName}</Td>
              <Td numeric>{formatNumber(line.qty)}</Td>
              {anyApproved ? <Td numeric>{line.approvedQty === null ? '' : formatNumber(line.approvedQty)}</Td> : null}
              <Td numeric>{formatMoney(line.rate)}</Td>
              <Td numeric>{formatMoney(line.taxableValue)}</Td>
            </tr>
          ))}
          <tr>
            <Td className="font-semibold">Total before GST</Td>
            <Td />
            {anyApproved ? <Td /> : null}
            <Td />
            <Td numeric className="font-semibold">
              {formatMoney(order.taxableValue, { withSymbol: true })}
            </Td>
          </tr>
        </tbody>
      </Table>
    </Panel>
  )
}
