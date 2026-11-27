import { Link } from 'react-router'

import { useBalances } from '../../api/operations'
import type { ItemKind } from '../../api/types'
import { PageHeader } from '../../components/Layout'
import { ErrorBox } from '../../components/Messages'
import { Panel } from '../../components/Panel'
import { Table, Td, Th } from '../../components/Table'
import { formatMoney, formatQty, formatRate } from '../../lib/format'
import { ITEM_KIND } from '../../lib/labels'

/** R6.5: stock on hand now, summed from the ledger by the backend, grouped raw / packaging / finished. */
export function StockPage() {
  const balances = useBalances()
  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Stock" />
      {balances.error ? <ErrorBox error={balances.error} /> : null}
      {(['RAW', 'PACKAGING', 'FG'] as ItemKind[]).map((kind) => (
        <Panel key={kind} title={ITEM_KIND[kind]}>
          <Table label={ITEM_KIND[kind]}>
            <thead>
              <tr>
                <Th>Item</Th>
                <Th numeric>On hand</Th>
                <Th numeric>Average cost (₹)</Th>
                <Th numeric>Value (₹)</Th>
              </tr>
            </thead>
            <tbody>
              {balances.data?.filter((balance) => balance.kind === kind).map((balance) => (
                <tr key={balance.itemId}>
                  <Td>
                    <Link to={`/stock/ledger/${balance.itemCode}`} className="text-primary hover:underline">{balance.itemName}</Link>
                  </Td>
                  <Td numeric>{formatQty(balance.qtyOnHand, balance.unit)}</Td>
                  <Td numeric>{balance.avgCost === null ? '' : `${formatRate(balance.avgCost)} per ${balance.unit}`}</Td>
                  <Td numeric>{formatMoney(balance.stockValue)}</Td>
                </tr>
              ))}
            </tbody>
          </Table>
        </Panel>
      ))}
    </div>
  )
}
