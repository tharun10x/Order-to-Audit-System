import { useMasterItems } from '../../api/masters'
import { useDashboard } from '../../api/reports'
import { ORDER_STATUSES, type Dashboard } from '../../api/types'
import { Facts, LinkButton, PageHeader } from '../../components/Layout'
import { EmptyState, ErrorBox } from '../../components/Messages'
import { Panel } from '../../components/Panel'
import { statusLabel } from '../../components/status'
import { Table, Td, Th } from '../../components/Table'
import { formatDate, formatMoney, formatNumber, formatPercent, formatQty } from '../../lib/format'
import { ITEM_KIND_ONE } from '../../lib/labels'

/**
 * R15.7, brief section 3: the owner's one screen. Plain figures and short tables, no charts (UI guide 7). Every
 * figure is the server's; the screen only formats it. Admin sees it too, to check it before the owner does.
 */
export function DashboardPage() {
  const dashboard = useDashboard()
  // Alerts name items and products by code; their names come from the item list (any status)
  const items = useMasterItems('all')
  const nameOf = (code: string) => items.data?.find((item) => item.code === code)?.name ?? code

  if (dashboard.error) {
    return <ErrorBox error={dashboard.error} />
  }
  if (!dashboard.data) {
    return <p className="text-sm text-muted">Loading the dashboard…</p>
  }
  const data = dashboard.data
  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Dashboard" action={<LinkButton to="/reports">Reports</LinkButton>}>
        <p className="mt-1 text-sm text-muted">{`Today is ${formatDate(data.businessDate)}.`}</p>
      </PageHeader>

      <Panel title="Money owed">
        <Facts items={[
          ['Total outstanding', <span key="total" className="num text-lg leading-[26px] font-semibold">{formatMoney(data.totalOutstanding, { withSymbol: true })}</span>],
        ]} />
        <TopCustomers data={data} />
      </Panel>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <Panel title="Today's orders">
          <Table label="Today's orders">
            <thead>
              <tr>
                <Th>Status</Th>
                <Th numeric>Orders</Th>
              </tr>
            </thead>
            <tbody>
              {ORDER_STATUSES.map((status) => (
                <tr key={status}>
                  <Td>{statusLabel(status)}</Td>
                  <Td numeric>{formatNumber(data.todaysOrders[status] ?? 0)}</Td>
                </tr>
              ))}
            </tbody>
          </Table>
        </Panel>

        <Panel title="Cost per packet">
          {data.costPerPacket.length === 0 ? (
            <EmptyState>No posted runs yet. The cost of each product's latest posted run appears here.</EmptyState>
          ) : (
            <Table label="Cost per packet">
              <thead>
                <tr>
                  <Th>Product</Th>
                  <Th>Latest run</Th>
                  <Th numeric>Cost per packet (₹)</Th>
                </tr>
              </thead>
              <tbody>
                {data.costPerPacket.map((cost) => (
                  <tr key={cost.productCode}>
                    <Td>{cost.productName}</Td>
                    <Td className="whitespace-nowrap">{`${cost.runNo}, ${formatDate(cost.runDate)}`}</Td>
                    <Td numeric className="font-semibold">{formatMoney(cost.costPerPacketRounded)}</Td>
                  </tr>
                ))}
              </tbody>
            </Table>
          )}
        </Panel>
      </div>

      <Panel title="Variance alerts, last 30 days">
        {data.varianceAlerts.length === 0 ? (
          <EmptyState>No flagged variances. Runs that use more than the tolerance, or yield less, appear here.</EmptyState>
        ) : (
          <Table label="Variance alerts">
            <thead>
              <tr>
                <Th>Run no</Th>
                <Th>Date</Th>
                <Th>Product</Th>
                <Th>What</Th>
                <Th numeric>Variance</Th>
              </tr>
            </thead>
            <tbody>
              {data.varianceAlerts.map((alert) => (
                <tr key={`${alert.runNo}-${alert.subject}`}>
                  <Td className="whitespace-nowrap">{alert.runNo}</Td>
                  <Td className="whitespace-nowrap">{formatDate(alert.runDate)}</Td>
                  <Td>{nameOf(alert.productCode)}</Td>
                  <Td>{alert.subject === 'YIELD' ? 'Yield (good output)' : nameOf(alert.subject)}</Td>
                  <Td numeric className="font-semibold text-danger">{`${formatPercent(alert.variancePct)} Flagged`}</Td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </Panel>

      <Panel title="Stock levels">
        <Table label="Stock levels">
          <thead>
            <tr>
              <Th>Item</Th>
              <Th>Kind</Th>
              <Th numeric>On hand</Th>
              <Th numeric>Reorder level</Th>
              <Th>Status</Th>
            </tr>
          </thead>
          <tbody>
            {data.stockLevels.map((level) => (
              <tr key={level.itemCode}>
                <Td>{level.itemName}</Td>
                <Td>{ITEM_KIND_ONE[level.kind] ?? level.kind}</Td>
                <Td numeric>{formatQty(level.qtyOnHand, level.unit)}</Td>
                <Td numeric>{level.reorderLevel === null ? '' : formatQty(level.reorderLevel, level.unit)}</Td>
                <Td>{level.lowStock ? <span className="font-semibold text-danger">Low stock</span> : ''}</Td>
              </tr>
            ))}
          </tbody>
        </Table>
      </Panel>
    </div>
  )
}

function TopCustomers({ data }: { data: Dashboard }) {
  if (data.topOutstanding.length === 0) {
    return <p className="mt-4 text-sm text-muted">Nothing is outstanding.</p>
  }
  return (
    <div className="mt-6">
      <Table label="Top customers by outstanding">
        <thead>
          <tr>
            <Th>Customer</Th>
            <Th numeric>Outstanding (₹)</Th>
          </tr>
        </thead>
        <tbody>
          {data.topOutstanding.map((customer) => (
            <tr key={customer.customerId}>
              <Td>{customer.customerName}</Td>
              <Td numeric>{formatMoney(customer.outstanding)}</Td>
            </tr>
          ))}
        </tbody>
      </Table>
    </div>
  )
}
