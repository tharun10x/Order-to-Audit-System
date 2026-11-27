import { usePurchases } from '../../api/operations'
import { useSession } from '../../auth/authContext'
import { can } from '../../auth/permissions'
import { LinkButton, PageHeader } from '../../components/Layout'
import { EmptyState, ErrorBox } from '../../components/Messages'
import { Panel } from '../../components/Panel'
import { Table, Td, Th } from '../../components/Table'
import { formatDate, formatMoney, formatQty, formatRate } from '../../lib/format'

/** R7: supplier bills, newest first. Quantities and rates are per base unit, as saved. */
export function PurchasesPage() {
  const session = useSession()
  const purchases = usePurchases()
  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Purchases"
        action={can.recordPurchase(session.user.role) ? <LinkButton variant="primary" to="/purchases/new">Record purchase</LinkButton> : undefined}
      />
      <Panel>
        {purchases.error ? <ErrorBox error={purchases.error} /> : null}
        {purchases.data && purchases.data.length === 0 ? (
          <EmptyState>No purchases yet. Supplier bills for raw materials and packaging appear here.</EmptyState>
        ) : null}
        {purchases.data && purchases.data.length > 0 ? (
          <Table label="Purchases">
            <thead>
              <tr>
                <Th>Date</Th>
                <Th>Bill no</Th>
                <Th>Supplier</Th>
                <Th>Items</Th>
                <Th numeric>Total (₹)</Th>
                <Th>Status</Th>
              </tr>
            </thead>
            <tbody>
              {purchases.data.map((purchase) => (
                <tr key={purchase.id}>
                  <Td className="whitespace-nowrap">{formatDate(purchase.purchaseDate)}</Td>
                  <Td>{purchase.billNo}</Td>
                  <Td>{purchase.supplierName}</Td>
                  <Td>
                    {purchase.lines.map((line) => (
                      <span key={line.itemCode} className="num block">
                        {`${line.itemName} ${formatQty(line.qty, line.unit)} at ₹${formatRate(line.rate)} per ${line.unit}`}
                      </span>
                    ))}
                  </Td>
                  <Td numeric>{formatMoney(purchase.total)}</Td>
                  <Td>{purchase.status === 'POSTED' ? <span className="font-medium text-success">Posted</span> : <span className="text-muted">Reversed</span>}</Td>
                </tr>
              ))}
            </tbody>
          </Table>
        ) : null}
      </Panel>
    </div>
  )
}
