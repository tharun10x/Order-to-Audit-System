import { useState } from 'react'

import { useUpdateOrderLines } from '../../api/queries'
import type { Order } from '../../api/types'
import { Button } from '../../components/Button'
import { TextInput } from '../../components/Field'
import { ErrorBox } from '../../components/Messages'
import { Panel } from '../../components/Panel'
import { Table, Td, Th } from '../../components/Table'

/**
 * R9.3: before approval the admin may change quantities (whole packets) and rates (per packet, before GST).
 * The values typed are sent as they are; the backend validates them and keeps the old ones in the audit log.
 */
interface EditableLine {
  itemCode: string
  itemName: string
  qty: string
  rate: string
}

const WHOLE = /^[1-9]\d*$/
const RUPEES = /^\d+(\.\d{1,2})?$/

export function EditLinesPanel({ order, onDone }: { order: Order; onDone: (notice: string) => void }) {
  const update = useUpdateOrderLines(order.id)
  const [lines, setLines] = useState<EditableLine[] | null>(null)
  const [showErrors, setShowErrors] = useState(false)

  if (!lines) {
    return (
      <Panel title="Edit lines">
        <p className="mb-3 text-sm">Change quantities or prices before approving.</p>
        <Button
          onClick={() =>
            setLines(order.lines.map((line) => ({
              itemCode: line.itemCode,
              itemName: line.itemName,
              qty: String(line.qty),
              rate: line.rate.toFixed(2),
            })))
          }
        >
          Edit lines
        </Button>
      </Panel>
    )
  }

  const qtyError = (line: EditableLine) => (WHOLE.test(line.qty) ? null : 'Enter whole packets, more than 0.')
  const rateError = (line: EditableLine) =>
    RUPEES.test(line.rate) && Number(line.rate) > 0 ? null : 'Enter a price above 0, at most 2 decimals.'
  const valid = lines.every((line) => !qtyError(line) && !rateError(line))

  function change(index: number, field: 'qty' | 'rate', value: string) {
    setLines((current) => current!.map((line, i) => (i === index ? { ...line, [field]: value.trim() } : line)))
  }

  function save() {
    if (!valid) {
      setShowErrors(true)
      return
    }
    update.mutate(
      lines!.map((line) => ({ itemCode: line.itemCode, qty: Number(line.qty), rate: Number(line.rate) })),
      {
        onSuccess: () => {
          setLines(null)
          setShowErrors(false)
          onDone('Lines saved.')
        },
      },
    )
  }

  return (
    <Panel title="Edit lines">
      <Table label="Edit order lines">
        <thead>
          <tr>
            <Th>Item</Th>
            <Th numeric>Quantity (pkt)</Th>
            <Th numeric>Rate (₹)</Th>
          </tr>
        </thead>
        <tbody>
          {lines.map((line, index) => (
            <tr key={line.itemCode}>
              <Td>{line.itemName}</Td>
              <Td numeric>
                <TextInput
                  aria-label={`Quantity of ${line.itemName}`}
                  inputMode="numeric"
                  value={line.qty}
                  onChange={(event) => change(index, 'qty', event.target.value)}
                  aria-invalid={showErrors && Boolean(qtyError(line))}
                  className="num max-w-32 text-right"
                />
                {showErrors && qtyError(line) ? <p className="mt-1 text-[13px] text-danger">{qtyError(line)}</p> : null}
              </Td>
              <Td numeric>
                <TextInput
                  aria-label={`Rate of ${line.itemName}`}
                  inputMode="decimal"
                  value={line.rate}
                  onChange={(event) => change(index, 'rate', event.target.value)}
                  aria-invalid={showErrors && Boolean(rateError(line))}
                  className="num max-w-32 text-right"
                />
                {showErrors && rateError(line) ? <p className="mt-1 text-[13px] text-danger">{rateError(line)}</p> : null}
              </Td>
            </tr>
          ))}
        </tbody>
      </Table>
      <div className="mt-4 flex flex-col gap-3">
        {update.error ? <ErrorBox error={update.error} /> : null}
        <div className="flex gap-3">
          <Button variant="primary" onClick={save} loading={update.isPending}>
            Save lines
          </Button>
          <Button onClick={() => setLines(null)} disabled={update.isPending}>
            Discard changes
          </Button>
        </div>
      </div>
    </Panel>
  )
}
