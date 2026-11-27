import { useOrders } from '../../api/queries'
import type { Order } from '../../api/types'
import { EmptyState, ErrorBox } from '../../components/Messages'
import { StatusText } from '../../components/StatusText'
import { formatDate, formatMoney, formatNumber } from '../../lib/format'

/** The salesman's own orders, newest first (the backend sorts and filters, R2.2), status as a word. */
export function MyOrdersPage() {
  const orders = useOrders()
  return (
    <>
      <h1 className="mb-4 text-xl leading-7 font-semibold">My orders</h1>
      {orders.error ? <ErrorBox error={orders.error} /> : null}
      {orders.data && orders.data.length === 0 ? (
        <EmptyState>No orders yet. Orders you submit appear here.</EmptyState>
      ) : null}
      <ul>
        {orders.data?.map((order) => (
          <OrderRow key={order.id} order={order} />
        ))}
      </ul>
    </>
  )
}

function OrderRow({ order }: { order: Order }) {
  const ordered = order.lines.reduce((sum, line) => sum + line.qty, 0)
  const approved = order.lines.every((line) => line.approvedQty !== null)
    ? order.lines.reduce((sum, line) => sum + (line.approvedQty ?? 0), 0)
    : null
  const packets =
    approved !== null && approved !== ordered
      ? `${formatNumber(approved)} of ${formatNumber(ordered)} pkt approved`
      : `${formatNumber(ordered)} pkt`
  return (
    <li className="border-b border-rule py-3">
      <div className="flex items-baseline justify-between gap-3">
        <span className="font-semibold">{order.customerName}</span>
        <StatusText status={order.status} />
      </div>
      <div className="num flex items-baseline justify-between gap-3 text-[13px] leading-[18px] text-muted">
        <span>{`${order.orderNo} · ${formatDate(order.orderDate)}`}</span>
        <span>{packets}</span>
      </div>
      <div className="num flex items-baseline justify-between gap-3 text-[13px] leading-[18px] text-muted">
        <span>{order.invoiceNo ? `Invoice ${order.invoiceNo}` : ''}</span>
        <span>{`${formatMoney(order.taxableValue, { withSymbol: true })} before GST`}</span>
      </div>
      {order.status === 'REJECTED' && order.remarks ? (
        <p className="text-[13px] leading-[18px] text-muted">{`Reason: ${order.remarks}`}</p>
      ) : null}
    </li>
  )
}
