import { useState } from 'react'

import { useItems, useSaveActuals } from '../../api/operations'
import type { Run } from '../../api/types'
import { Button } from '../../components/Button'
import { Field, TextInput } from '../../components/Field'
import { Select } from '../../components/Layout'
import { ErrorBox } from '../../components/Messages'
import { Panel } from '../../components/Panel'
import { Table, Td, Th } from '../../components/Table'
import { isQuantity } from '../../lib/decimal'
import { formatQty } from '../../lib/format'

/**
 * R8.4: what was really issued (scrap included) and how much of it was scrap or damaged, per recipe line, and the
 * good output. Quantities can be typed in any unit the item accepts (oil in L or ml); the backend converts.
 * Saving keeps the run a DRAFT: nothing moves until it is posted.
 */
interface Entry {
  actual: string
  scrap: string
  unit: string
}

export function ActualsForm({ run, onSaved }: { run: Run; onSaved: () => void }) {
  const items = useItems()
  const save = useSaveActuals(run.id)
  const [output, setOutput] = useState(run.actualOutput === null ? '' : String(run.actualOutput))
  const [entries, setEntries] = useState<Record<string, Entry>>(() =>
    Object.fromEntries(run.lines.map((line) => [line.itemCode, {
      actual: line.actualQty === null ? '' : String(line.actualQty),
      scrap: line.actualQty === null ? '' : String(line.scrapQty),
      unit: line.unit,
    }])))
  const [showErrors, setShowErrors] = useState(false)

  const unitsOf = (itemCode: string, base: string) => items.data?.find((item) => item.code === itemCode)?.units ?? [base]
  const quantityError = (text: string, required: boolean) =>
    (text.trim() === '' && !required) || isQuantity(text) ? null : 'Enter a quantity (0 or more, up to 4 decimals).'
  const outputError = isQuantity(output) && Number(output) > 0 ? null : 'Enter the good output in packets, more than 0.'
  const scrapError = (entry: Entry) =>
    isQuantity(entry.actual) && isQuantity(entry.scrap) && Number(entry.scrap) > Number(entry.actual)
      ? 'Scrap cannot be more than the quantity issued.'
      : null
  const valid = !outputError && run.lines.every((line) => {
    const entry = entries[line.itemCode]
    return !quantityError(entry.actual, true) && !quantityError(entry.scrap, false) && !scrapError(entry)
  })

  function change(itemCode: string, patch: Partial<Entry>) {
    setEntries((current) => ({ ...current, [itemCode]: { ...current[itemCode], ...patch } }))
  }

  function submit() {
    if (!valid) {
      setShowErrors(true)
      return
    }
    save.mutate(
      {
        actualOutput: Number(output),
        lines: run.lines.map((line) => {
          const entry = entries[line.itemCode]
          return {
            itemCode: line.itemCode,
            actualQty: Number(entry.actual),
            scrapQty: entry.scrap.trim() === '' ? 0 : Number(entry.scrap),
            uomCode: entry.unit,
          }
        }),
      },
      { onSuccess: onSaved },
    )
  }

  return (
    <Panel title="Enter actuals">
      <Table label="Actuals">
        <thead>
          <tr>
            <Th>Item</Th>
            <Th numeric>Planned</Th>
            <Th numeric>Actual issued (incl. scrap)</Th>
            <Th numeric>Scrap / damaged</Th>
            <Th>Unit</Th>
          </tr>
        </thead>
        <tbody>
          {run.lines.map((line) => {
            const entry = entries[line.itemCode]
            const units = unitsOf(line.itemCode, line.unit)
            const actualError = showErrors ? quantityError(entry.actual, true) : null
            const lineScrapError = showErrors ? quantityError(entry.scrap, false) ?? scrapError(entry) : null
            return (
              <tr key={line.itemCode}>
                <Td>{line.itemName}</Td>
                <Td numeric>{formatQty(line.plannedQty, line.unit)}</Td>
                <Td numeric>
                  <TextInput aria-label={`Actual ${line.itemName}`} inputMode="decimal" value={entry.actual}
                    className="num max-w-32 text-right" onChange={(event) => change(line.itemCode, { actual: event.target.value })} />
                  {actualError ? <p className="mt-1 text-[13px] text-danger">{actualError}</p> : null}
                </Td>
                <Td numeric>
                  <TextInput aria-label={`Scrap ${line.itemName}`} inputMode="decimal" value={entry.scrap} placeholder="0"
                    className="num max-w-32 text-right" onChange={(event) => change(line.itemCode, { scrap: event.target.value })} />
                  {lineScrapError ? <p className="mt-1 text-[13px] text-danger">{lineScrapError}</p> : null}
                </Td>
                <Td>
                  {units.length > 1 ? (
                    <Select aria-label={`Unit for ${line.itemName}`} value={entry.unit} className="min-w-20"
                      onChange={(event) => change(line.itemCode, { unit: event.target.value })}>
                      {units.map((unit) => <option key={unit} value={unit}>{unit}</option>)}
                    </Select>
                  ) : (
                    <span>{entry.unit}</span>
                  )}
                </Td>
              </tr>
            )
          })}
        </tbody>
      </Table>
      <div className="mt-4 max-w-xs">
        <Field label="Good output (packets)" error={showErrors ? outputError ?? undefined : undefined}>
          {({ id, describedBy, invalid }) => (
            <TextInput id={id} inputMode="numeric" value={output} className="num" aria-describedby={describedBy}
              aria-invalid={invalid} onChange={(event) => setOutput(event.target.value)} />
          )}
        </Field>
      </div>
      <div className="mt-4 flex flex-col gap-3">
        {save.error ? <ErrorBox error={save.error} /> : null}
        <div>
          <Button variant="primary" onClick={submit} loading={save.isPending}>Save actuals</Button>
        </div>
      </div>
    </Panel>
  )
}
