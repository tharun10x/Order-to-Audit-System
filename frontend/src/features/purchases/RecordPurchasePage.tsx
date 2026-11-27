import { useState } from 'react'
import { Link } from 'react-router'

import { useItems, useRecordPurchase, useSuppliers } from '../../api/operations'
import type { Item, Purchase } from '../../api/types'
import { useSession } from '../../auth/authContext'
import { Button } from '../../components/Button'
import { Field, TextInput } from '../../components/Field'
import { PageHeader, Select } from '../../components/Layout'
import { ErrorBox, SuccessLine } from '../../components/Messages'
import { Panel } from '../../components/Panel'
import { Table, Td, Th } from '../../components/Table'
import { isAmount, isQuantity } from '../../lib/decimal'
import { formatMoney, formatQty } from '../../lib/format'

/**
 * R7.1: a supplier bill, entered as printed: quantity and rate in the unit on the bill (60000 g at 0.045, or
 * 60 kg at 45). The units offered per item are the ones the backend accepts (R3.3). One line per item (R6.8).
 * The line amount here is an estimate; the saved amount is the server's.
 */
interface Line {
  key: number
  itemCode: string
  qty: string
  unit: string
  rate: string
}

let nextKey = 1
const emptyLine = (): Line => ({ key: nextKey++, itemCode: '', qty: '', unit: '', rate: '' })

export function RecordPurchasePage() {
  const { businessDate } = useSession()
  const suppliers = useSuppliers()
  const items = useItems()
  const record = useRecordPurchase()
  const [supplierId, setSupplierId] = useState('')
  const [billNo, setBillNo] = useState('')
  const [purchaseDate, setPurchaseDate] = useState(businessDate)
  const [lines, setLines] = useState<Line[]>([emptyLine()])
  const [showErrors, setShowErrors] = useState(false)
  const [recorded, setRecorded] = useState<Purchase | null>(null)

  if (suppliers.error || items.error) {
    return <ErrorBox error={suppliers.error ?? items.error} />
  }
  if (!suppliers.data || !items.data) {
    return <p className="text-sm text-muted">Loading…</p>
  }
  // Finished goods are made, not bought (R7.1)
  const purchasable = items.data.filter((item) => item.kind !== 'FG')
  const itemOf = (code: string) => purchasable.find((item) => item.code === code)

  const lineErrors = (line: Line) => ({
    item: line.itemCode ? null : 'Pick an item.',
    qty: isQuantity(line.qty) && Number(line.qty) > 0 ? null : 'Enter a quantity above 0 (up to 4 decimals).',
    rate: isAmount(line.rate) || isQuantity(line.rate) ? null : 'Enter the rate per unit, for example 45 or 0.045.',
  })
  const formErrors = {
    supplier: supplierId ? null : 'Pick the supplier.',
    billNo: billNo.trim() ? null : 'Enter the bill number printed on the supplier bill.',
    date: purchaseDate ? null : 'Pick the bill date.',
  }
  const valid = Object.values(formErrors).every((error) => !error)
    && lines.every((line) => Object.values(lineErrors(line)).every((error) => !error))

  function change(key: number, patch: Partial<Line>) {
    setLines((current) => current.map((line) => (line.key === key ? { ...line, ...patch } : line)))
  }

  function chooseItem(key: number, itemCode: string) {
    // The unit starts as the item's base unit; the item's other units are in the unit selector
    change(key, { itemCode, unit: itemOf(itemCode)?.baseUnit ?? '' })
  }

  function submit() {
    if (!valid) {
      setShowErrors(true)
      return
    }
    record.mutate(
      {
        supplierId: Number(supplierId),
        purchaseDate,
        billNo: billNo.trim(),
        lines: lines.map((line) => ({ itemCode: line.itemCode, qty: Number(line.qty), uomCode: line.unit, rate: Number(line.rate) })),
      },
      {
        onSuccess: (purchase) => {
          setRecorded(purchase)
          setBillNo('')
          setLines([emptyLine()])
          setShowErrors(false)
        },
      },
    )
  }

  const chosen = new Set(lines.map((line) => line.itemCode).filter(Boolean))

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Record purchase">
        <p className="mt-1 text-sm"><Link to="/purchases" className="text-primary hover:underline">All purchases</Link></p>
      </PageHeader>
      {recorded ? (
        <SuccessLine>{`Bill ${recorded.billNo} recorded: ${formatMoney(recorded.total, { withSymbol: true })}. Stock is updated.`}</SuccessLine>
      ) : null}
      <Panel>
        <div className="grid max-w-3xl grid-cols-1 gap-4 sm:grid-cols-3">
          <Field label="Supplier" error={showErrors ? formErrors.supplier ?? undefined : undefined}>
            {({ id }) => (
              <Select id={id} value={supplierId} onChange={(event) => setSupplierId(event.target.value)}>
                <option value="">Pick a supplier</option>
                {suppliers.data.map((supplier) => <option key={supplier.id} value={supplier.id}>{supplier.name}</option>)}
              </Select>
            )}
          </Field>
          <Field label="Bill no" error={showErrors ? formErrors.billNo ?? undefined : undefined}>
            {({ id }) => <TextInput id={id} value={billNo} maxLength={50} onChange={(event) => setBillNo(event.target.value)} />}
          </Field>
          <Field label="Bill date" error={showErrors ? formErrors.date ?? undefined : undefined}>
            {({ id }) => <TextInput id={id} type="date" value={purchaseDate} onChange={(event) => setPurchaseDate(event.target.value)} />}
          </Field>
        </div>

        <div className="mt-6">
          <Table label="Bill lines">
            <thead>
              <tr>
                <Th>Item</Th>
                <Th numeric>Quantity</Th>
                <Th>Unit</Th>
                <Th numeric>Rate per unit (₹)</Th>
                <Th numeric>Amount, estimate (₹)</Th>
                <Th><span className="sr-only">Remove</span></Th>
              </tr>
            </thead>
            <tbody>
              {lines.map((line, index) => {
                const errors = lineErrors(line)
                const item = itemOf(line.itemCode)
                return (
                  <tr key={line.key}>
                    <Td>
                      <Select aria-label={`Item on line ${index + 1}`} value={line.itemCode}
                        onChange={(event) => chooseItem(line.key, event.target.value)}>
                        <option value="">Pick an item</option>
                        {purchasable.map((option: Item) => (
                          <option key={option.code} value={option.code} disabled={option.code !== line.itemCode && chosen.has(option.code)}>
                            {option.name}
                          </option>
                        ))}
                      </Select>
                      {showErrors && errors.item ? <p className="mt-1 text-[13px] text-danger">{errors.item}</p> : null}
                    </Td>
                    <Td numeric>
                      <TextInput aria-label={`Quantity on line ${index + 1}`} inputMode="decimal" value={line.qty}
                        className="num max-w-32 text-right" onChange={(event) => change(line.key, { qty: event.target.value })} />
                      {showErrors && errors.qty ? <p className="mt-1 text-[13px] text-danger">{errors.qty}</p> : null}
                    </Td>
                    <Td>
                      <Select aria-label={`Unit on line ${index + 1}`} value={line.unit} disabled={!item}
                        onChange={(event) => change(line.key, { unit: event.target.value })} className="min-w-20">
                        {(item?.units ?? []).map((unit) => <option key={unit} value={unit}>{unit}</option>)}
                      </Select>
                    </Td>
                    <Td numeric>
                      <TextInput aria-label={`Rate on line ${index + 1}`} inputMode="decimal" value={line.rate}
                        className="num max-w-32 text-right" onChange={(event) => change(line.key, { rate: event.target.value })} />
                      {showErrors && errors.rate ? <p className="mt-1 text-[13px] text-danger">{errors.rate}</p> : null}
                    </Td>
                    <Td numeric className="text-muted">{estimate(line)}</Td>
                    <Td>
                      {lines.length > 1 ? (
                        <button type="button" className="min-h-11 px-2 text-sm text-danger"
                          onClick={() => setLines((current) => current.filter((other) => other.key !== line.key))}>
                          Remove
                        </button>
                      ) : null}
                    </Td>
                  </tr>
                )
              })}
            </tbody>
          </Table>
          <p className="mt-2 text-[13px] leading-[18px] text-muted">
            Amounts are estimates for checking the bill. The amount saved is calculated by the server.
          </p>
          <div className="mt-3">
            <Button onClick={() => setLines((current) => [...current, emptyLine()])}
              disabled={lines.length >= purchasable.length}>
              Add line
            </Button>
          </div>
        </div>

        <div className="mt-6 flex flex-col gap-3">
          {record.error ? <ErrorBox error={record.error} /> : null}
          <div>
            <Button variant="primary" onClick={submit} loading={record.isPending}>Record purchase</Button>
          </div>
        </div>
      </Panel>
    </div>
  )
}

/** Quantity × rate for checking against the bill: shown only, never sent. */
function estimate(line: Line): string {
  if (!isQuantity(line.qty) || !(isAmount(line.rate) || isQuantity(line.rate)) || !line.unit) {
    return ''
  }
  const amount = Number(line.qty) * Number(line.rate)
  return `${formatMoney(Math.round(amount * 100) / 100)} for ${formatQty(Number(line.qty), line.unit)}`
}
