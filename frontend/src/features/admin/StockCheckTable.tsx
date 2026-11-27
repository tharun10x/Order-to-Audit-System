import type { StockCheck } from '../../api/types'
import { Table, Td, Th } from '../../components/Table'
import { formatNumber } from '../../lib/format'

/** R9.4: per line, what is ordered against what is free to promise. A shortage is in red and in words. */
/** names: product name per item code, from the order's lines, so people read names rather than codes. */
export function StockCheckTable({ stockCheck, names }: { stockCheck: StockCheck; names: Record<string, string> }) {
  return (
    <Table label="Stock check">
      <thead>
        <tr>
          <Th>Item</Th>
          <Th numeric>Ordered (pkt)</Th>
          <Th numeric>On hand</Th>
          <Th numeric>Reserved</Th>
          <Th numeric>Available</Th>
          <Th numeric>Short</Th>
          <Th numeric>Batches needed</Th>
        </tr>
      </thead>
      <tbody>
        {stockCheck.lines.map((line) => (
          <tr key={line.itemCode}>
            <Td>{names[line.itemCode] ?? line.itemCode}</Td>
            <Td numeric>{formatNumber(line.ordered)}</Td>
            <Td numeric>{formatNumber(line.onHand)}</Td>
            <Td numeric>{formatNumber(line.reserved)}</Td>
            <Td numeric>{formatNumber(line.available)}</Td>
            <Td numeric className={line.shortage > 0 ? 'font-semibold text-danger' : ''}>
              {line.shortage > 0 ? `Short by ${formatNumber(line.shortage)}` : 'None'}
            </Td>
            <Td numeric>
              {line.shortage > 0 ? (line.batchesNeeded === null ? 'No recipe' : formatNumber(line.batchesNeeded)) : ''}
            </Td>
          </tr>
        ))}
      </tbody>
    </Table>
  )
}
