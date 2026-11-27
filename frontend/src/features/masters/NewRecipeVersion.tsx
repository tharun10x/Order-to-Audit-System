import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { recipesQuery, useCreateRecipe } from '../../api/masters'
import type { Bom, Item } from '../../api/types'
import { Button } from '../../components/Button'
import { Field, TextInput } from '../../components/Field'
import { Select } from '../../components/Layout'
import { ErrorBox } from '../../components/Messages'
import { Table, Td, Th } from '../../components/Table'
import { formatQty } from '../../lib/format'
import { lineQtyError, outputQtyError } from '../../lib/masterRules'
import { FormFooter } from './formParts'

/**
 * R5.8: a new recipe version, saved inactive. It starts from the newest version's lines, which the admin edits.
 * R5.10: "Copy ingredients from" another product's active recipe: the server copies those ingredient lines (same
 * items, same quantities per batch) and this form adds the rest, usually the pack's own packaging and yield.
 * The copy is independent: a later change to the source recipe does not change this one.
 */
interface Line {
  key: number
  itemCode: string
  qty: string
  unit: string
}

let nextKey = 1
const emptyLine = (): Line => ({ key: nextKey++, itemCode: '', qty: '', unit: '' })

export function NewRecipeVersion({ product, latest, items, onSaved, onCancel }: {
  product: Item
  /** The newest version, to start from; undefined for a product's first recipe. */
  latest: Bom | undefined
  /** Every item (any status): lines pick active raw materials and packaging; copy-from picks products. */
  items: Item[]
  onSaved: (bom: Bom) => void
  onCancel: () => void
}) {
  const create = useCreateRecipe()
  const queryClient = useQueryClient()
  const [outputQty, setOutputQty] = useState(latest ? String(latest.outputQty) : '')
  const [lines, setLines] = useState<Line[]>(() =>
    latest ? latest.lines.map((line) => ({ key: nextKey++, itemCode: line.itemCode, qty: String(line.qty), unit: line.unit })) : [emptyLine()])
  const [copyFrom, setCopyFrom] = useState<{ product: Item; bom: Bom } | null>(null)
  const [copyError, setCopyError] = useState<unknown>(null)
  const [copying, setCopying] = useState(false)
  const [removedByCopy, setRemovedByCopy] = useState<string[]>([])
  const [showErrors, setShowErrors] = useState(false)

  const itemOf = (code: string) => items.find((item) => item.code === code)
  // M11: a recipe uses raw materials and packaging only, and only active ones
  const usable = items.filter((item) => item.kind !== 'FG' && item.active)
  // R5.10: any other product whose active recipe can be copied
  const sources = items.filter((item) => item.kind === 'FG' && item.hasActiveBom && item.code !== product.code)
  const copied = copyFrom ? copyFrom.bom.lines.filter((line) => line.lineKind === 'INGREDIENT') : []
  const copiedCodes = new Set(copied.map((line) => line.itemCode))

  async function chooseSource(code: string) {
    setCopyError(null)
    setRemovedByCopy([])
    if (!code) {
      setCopyFrom(null)
      return
    }
    setCopying(true)
    try {
      const versions = await queryClient.fetchQuery(recipesQuery(code))
      const bom = versions.find((version) => version.active)
      if (!bom) {
        setCopyError(new Error(`${itemOf(code)?.name ?? code} has no active recipe to copy from.`))
        setCopyFrom(null)
        return
      }
      // An item appears once per version (R5.8): lines the copy brings in are taken out of the lines below
      const brought = new Set(bom.lines.filter((line) => line.lineKind === 'INGREDIENT').map((line) => line.itemCode))
      setRemovedByCopy(lines.filter((line) => brought.has(line.itemCode)).map((line) => itemOf(line.itemCode)?.name ?? line.itemCode))
      setLines((current) => current.filter((line) => !brought.has(line.itemCode)))
      setCopyFrom({ product: itemOf(code)!, bom })
    } catch (error) {
      setCopyError(error)
      setCopyFrom(null)
    } finally {
      setCopying(false)
    }
  }

  function change(key: number, patch: Partial<Line>) {
    setLines((current) => current.map((line) => (line.key === key ? { ...line, ...patch } : line)))
  }

  const lineErrors = (line: Line) => {
    const item = itemOf(line.itemCode)
    return {
      item: !line.itemCode
        ? 'Pick an item.'
        : !item?.active
          ? `${item?.name ?? line.itemCode} is inactive. Remove this line, or reactivate the item first.`
          : null,
      qty: lineQtyError(line.qty),
    }
  }
  const hasIngredient = copied.length > 0 || lines.some((line) => itemOf(line.itemCode)?.kind === 'RAW')
  const errors: Record<string, string | null> = {
    outputQty: outputQtyError(outputQty),
    lines: lines.some((line) => Object.values(lineErrors(line)).some(Boolean)) ? 'A line needs correcting.' : null,
    ingredient: hasIngredient ? null : 'A recipe needs at least one raw material. Add one, or copy the ingredients of another product.',
  }

  function submit() {
    if (Object.values(errors).some(Boolean)) {
      setShowErrors(true)
      return
    }
    create.mutate(
      {
        productCode: product.code,
        outputQty: outputQty.trim(),
        copyIngredientsFrom: copyFrom ? copyFrom.product.code : null,
        lines: lines.map((line) => ({ itemCode: line.itemCode, qty: line.qty.trim(), uomCode: line.unit })),
      },
      { onSuccess: onSaved },
    )
  }

  const chosen = new Set([...lines.map((line) => line.itemCode), ...copiedCodes])

  return (
    <div className="flex flex-col gap-6">
      <div className="grid max-w-3xl grid-cols-1 gap-4 sm:grid-cols-2">
        <Field label="Packets per batch" error={showErrors ? errors.outputQty ?? undefined : undefined}
          hint="The expected yield of one batch, a whole number.">
          {({ id, describedBy }) => (
            <TextInput id={id} aria-describedby={describedBy} inputMode="numeric" className="num" value={outputQty}
              onChange={(event) => setOutputQty(event.target.value)} />
          )}
        </Field>
        <Field label="Copy ingredients from" hint="Optional. Copies the raw materials of that product's active recipe, at the same quantities per batch.">
          {({ id, describedBy }) => (
            <Select id={id} aria-describedby={describedBy} value={copyFrom?.product.code ?? ''} disabled={copying}
              onChange={(event) => void chooseSource(event.target.value)}>
              <option value="">Do not copy</option>
              {sources.map((source) => <option key={source.code} value={source.code}>{source.name}</option>)}
            </Select>
          )}
        </Field>
      </div>

      {copyError ? <ErrorBox error={copyError} /> : null}
      {copying ? <p className="text-sm text-muted">Reading the recipe to copy…</p> : null}
      {copyFrom ? (
        <div>
          <h3 className="mb-2 text-base font-semibold">
            {`Ingredients copied from ${copyFrom.product.name}, version ${copyFrom.bom.version}`}
          </h3>
          <Table label="Copied ingredients">
            <thead>
              <tr>
                <Th>Item</Th>
                <Th numeric>Quantity per batch</Th>
              </tr>
            </thead>
            <tbody>
              {copied.map((line) => (
                <tr key={line.itemCode}>
                  <Td>{line.itemName}</Td>
                  <Td numeric>{formatQty(line.qty, line.unit)}</Td>
                </tr>
              ))}
            </tbody>
          </Table>
          {removedByCopy.length > 0 ? (
            <p className="mt-2 text-[13px] leading-[18px] text-muted">
              {`Taken out of the lines below, because the copy brings them in: ${removedByCopy.join(', ')}.`}
            </p>
          ) : null}
        </div>
      ) : null}

      <div>
        <h3 className="mb-2 text-base font-semibold">{copyFrom ? 'Other lines' : 'Lines'}</h3>
        <Table label="Recipe lines">
          <thead>
            <tr>
              <Th>Item</Th>
              <Th>Kind</Th>
              <Th numeric>Quantity per batch</Th>
              <Th>Unit</Th>
              <Th><span className="sr-only">Remove</span></Th>
            </tr>
          </thead>
          <tbody>
            {lines.map((line, index) => {
              const item = itemOf(line.itemCode)
              const lineError = lineErrors(line)
              // A prefilled item that has since been deactivated stays visible, so its error makes sense
              const options = item && !usable.includes(item) ? [item, ...usable] : usable
              return (
                <tr key={line.key}>
                  <Td>
                    <Select aria-label={`Item on line ${index + 1}`} value={line.itemCode} className="min-w-48"
                      onChange={(event) => change(line.key, { itemCode: event.target.value, unit: itemOf(event.target.value)?.baseUnit ?? '' })}>
                      <option value="">Pick an item</option>
                      {(['RAW', 'PACKAGING'] as const).map((kind) => (
                        <optgroup key={kind} label={kind === 'RAW' ? 'Raw materials' : 'Packaging'}>
                          {options.filter((option) => option.kind === kind).map((option) => (
                            <option key={option.code} value={option.code}
                              disabled={option.code !== line.itemCode && chosen.has(option.code)}>
                              {option.name}
                            </option>
                          ))}
                        </optgroup>
                      ))}
                    </Select>
                    {showErrors && lineError.item ? <p className="mt-1 text-[13px] text-danger">{lineError.item}</p> : null}
                  </Td>
                  <Td className="whitespace-nowrap">{item ? (item.kind === 'RAW' ? 'Ingredient' : 'Packaging') : ''}</Td>
                  <Td numeric>
                    <TextInput aria-label={`Quantity on line ${index + 1}`} inputMode="decimal" value={line.qty}
                      className="num max-w-32 text-right" onChange={(event) => change(line.key, { qty: event.target.value })} />
                    {showErrors && lineError.qty ? <p className="mt-1 text-[13px] text-danger">{lineError.qty}</p> : null}
                  </Td>
                  <Td>
                    <Select aria-label={`Unit on line ${index + 1}`} value={line.unit} disabled={!item} className="min-w-20"
                      onChange={(event) => change(line.key, { unit: event.target.value })}>
                      {(item?.units ?? []).map((unit) => <option key={unit} value={unit}>{unit}</option>)}
                    </Select>
                  </Td>
                  <Td>
                    <button type="button" className="min-h-11 px-2 text-sm text-danger"
                      onClick={() => setLines((current) => current.filter((other) => other.key !== line.key))}>
                      Remove
                    </button>
                  </Td>
                </tr>
              )
            })}
          </tbody>
        </Table>
        {lines.length === 0 ? <p className="py-3 text-sm text-muted">No lines of its own yet. Add the packaging this pack uses.</p> : null}
        <p className="mt-2 text-[13px] leading-[18px] text-muted">
          Quantities are for one batch, in any unit the item accepts; they are saved in its base unit.
        </p>
        <div className="mt-3">
          <Button onClick={() => setLines((current) => [...current, emptyLine()])}>Add line</Button>
        </div>
      </div>

      {showErrors && errors.ingredient ? <ErrorBox message={errors.ingredient} /> : null}
      <FormFooter showErrors={showErrors} errors={{ outputQty: errors.outputQty, lines: errors.lines }} error={create.error}
        pending={create.isPending} label="Save as new version" onSubmit={submit}
        secondary={<Button onClick={onCancel} disabled={create.isPending}>Discard</Button>} />
    </div>
  )
}
