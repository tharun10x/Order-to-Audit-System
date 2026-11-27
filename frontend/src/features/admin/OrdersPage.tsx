import { Link, useNavigate, useParams, useSearchParams } from 'react-router'

import { useOrders } from '../../api/queries'
import { ORDER_STATUSES, type Order, type OrderStatus } from '../../api/types'
import { Field } from '../../components/Field'
import { EmptyState, ErrorBox } from '../../components/Messages'
import { Panel } from '../../components/Panel'
import { statusLabel } from '../../components/status'
import { StatusText } from '../../components/StatusText'
import { Table, Td, Th } from '../../components/Table'
import { formatDate, formatMoney, formatNumber } from '../../lib/format'
import { OrderDetail } from './OrderDetail'

/** Brief 4.2: incoming orders, then the selected order's lines and stock check below (UI guide section 7). */
export function OrdersPage() {
  const { orderId } = useParams()
  const [params, setParams] = useSearchParams()
  const statusParam = params.get('status')
  const status = ORDER_STATUSES.includes(statusParam as OrderStatus) ? (statusParam as OrderStatus) : undefined
  const orders = useOrders(status)
  const navigate = useNavigate()
  const selectedId = orderId ? Number(orderId) : null
  const query = params.toString() ? `?${params.toString()}` : ''

  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-2xl font-semibold">Orders</h1>
      <Panel>
        <div className="mb-4 max-w-60">
          <Field label="Status">
            {({ id }) => (
              <select
                id={id}
                value={status ?? ''}
                onChange={(event) => setParams(event.target.value ? { status: event.target.value } : {})}
                className="min-h-11 w-full rounded-control border border-border bg-surface px-3 text-sm"
              >
                <option value="">All orders</option>
                {ORDER_STATUSES.map((value) => (
                  <option key={value} value={value}>
                    {statusLabel(value)}
                  </option>
                ))}
              </select>
            )}
          </Field>
        </div>
        {orders.error ? <ErrorBox error={orders.error} /> : null}
        {orders.data && orders.data.length === 0 ? (
          <EmptyState>
            {status
              ? `No ${statusLabel(status).toLowerCase()} orders.`
              : 'No orders yet. New orders from salesmen appear here.'}
          </EmptyState>
        ) : null}
        {orders.data && orders.data.length > 0 ? (
          <Table label="Orders">
            <thead>
              <tr>
                <Th>Order no</Th>
                <Th>Date</Th>
                <Th>Customer</Th>
                <Th>Salesman</Th>
                <Th numeric>Packets</Th>
                <Th numeric>Value before GST (₹)</Th>
                <Th>Status</Th>
                <Th>Invoice</Th>
              </tr>
            </thead>
            <tbody>
              {orders.data.map((order) => (
                <OrderRow
                  key={order.id}
                  order={order}
                  selected={order.id === selectedId}
                  href={`/orders/${order.id}${query}`}
                  onSelect={() => navigate(`/orders/${order.id}${query}`)}
                />
              ))}
            </tbody>
          </Table>
        ) : null}
      </Panel>
      {selectedId ? (
        <OrderDetail key={selectedId} orderId={selectedId} />
      ) : orders.data && orders.data.length > 0 ? (
        <p className="text-sm text-muted">Select an order to see its lines and stock check.</p>
      ) : null}
    </div>
  )
}

function OrderRow({ order, selected, href, onSelect }: { order: Order; selected: boolean; href: string; onSelect: () => void }) {
  const packets = order.lines.reduce((sum, line) => sum + (line.approvedQty ?? line.qty), 0)
  return (
    <tr
      onClick={onSelect}
      aria-selected={selected}
      className={`cursor-pointer ${selected ? 'bg-primary-tint' : 'hover:bg-bg'}`}
    >
      <Td className="whitespace-nowrap">
        <Link to={href} className="font-medium text-primary underline-offset-2 hover:underline" onClick={(event) => event.stopPropagation()}>
          {order.orderNo}
        </Link>
      </Td>
      <Td className="whitespace-nowrap">{formatDate(order.orderDate)}</Td>
      <Td>{order.customerName}</Td>
      <Td>{order.salesman}</Td>
      <Td numeric>{formatNumber(packets)}</Td>
      <Td numeric>{formatMoney(order.taxableValue)}</Td>
      <Td>
        <StatusText status={order.status} />
      </Td>
      <Td className="whitespace-nowrap">{order.invoiceNo ?? ''}</Td>
    </tr>
  )
}
