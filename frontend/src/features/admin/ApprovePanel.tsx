import { useApproveOrder } from '../../api/queries'
import type { Order, StockCheck } from '../../api/types'
import { Button } from '../../components/Button'
import { ErrorBox } from '../../components/Messages'
import { formatNumber } from '../../lib/format'

/**
 * R9.5: an order with a shortage cannot be approved as it stands. The admin can approve what is in stock now
 * ("Approve 88 packets"), wait for production, or reject. The available quantity comes from the backend's stock
 * check; the backend checks it again under lock when the button is pressed.
 */
export function ApprovePanel({ order, stockCheck, onDone }: { order: Order; stockCheck: StockCheck; onDone: (notice: string) => void }) {
  const approve = useApproveOrder(order.id)
  const short = stockCheck.lines.filter((line) => line.shortage > 0)
  const nameOf = (itemCode: string) => order.lines.find((line) => line.itemCode === itemCode)?.itemName ?? itemCode

  // What can be promised now: the whole line if it is covered, otherwise the whole packets available
  const partial = stockCheck.lines.map((line) => ({
    itemCode: line.itemCode,
    approvedQty: Math.min(line.ordered, Math.max(0, Math.floor(line.available))),
  }))
  const partialPossible = partial.every((line) => line.approvedQty > 0)
  const partialTotal = partial.reduce((sum, line) => sum + line.approvedQty, 0)
  const partialLabel =
    stockCheck.lines.length === 1 ? `Approve ${formatNumber(partialTotal)} packets` : 'Approve available quantities'

  const approveAll = () => approve.mutate(null, { onSuccess: () => onDone('Order approved.') })
  const approvePartial = () =>
    approve.mutate(partial, {
      onSuccess: () => onDone(`Order approved for ${formatNumber(partialTotal)} packets.`),
    })

  return (
    <div className="mt-6 flex flex-col gap-3">
      {approve.error ? <ErrorBox error={approve.error} /> : null}
      {short.length === 0 ? (
        <div>
          <Button variant="primary" onClick={approveAll} loading={approve.isPending}>
            Approve order
          </Button>
        </div>
      ) : (
        <>
          <ul className="text-sm text-danger">
            {short.map((line) => (
              <li key={line.itemCode} className="font-semibold">
                {`${nameOf(line.itemCode)}: short by ${formatNumber(line.shortage)} packets`}
                {line.batchesNeeded ? ` (${formatNumber(line.batchesNeeded)} ${line.batchesNeeded === 1 ? 'batch' : 'batches'} of production would cover it)` : ''}
              </li>
            ))}
          </ul>
          <div className="flex flex-wrap items-center gap-3">
            {partialPossible ? (
              <Button variant="primary" onClick={approvePartial} loading={approve.isPending}>
                {partialLabel}
              </Button>
            ) : null}
            <Button disabled aria-describedby="approve-why">
              Approve order
            </Button>
          </div>
          <p id="approve-why" className="text-[13px] leading-[18px] text-muted">
            {partialPossible
              ? 'The full order cannot be approved until production covers the shortage. Approve what is in stock now, wait, or reject the order.'
              : 'Nothing is in stock for at least one line. Wait for production or reject the order.'}
          </p>
        </>
      )}
    </div>
  )
}
