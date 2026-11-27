import { Lock } from 'lucide-react'
import { useState } from 'react'

import { useSaveItem } from '../../api/masters'
import type { Item, ItemKind } from '../../api/types'
import { Field, TextInput } from '../../components/Field'
import { Select } from '../../components/Layout'
import { BASE_UNIT, ITEM_KIND_ONE } from '../../lib/labels'
import {
  codeError, gstRateError, hsnError, nameError, normaliseCode, optional, packetsPerCartonError, packWeightError,
  priceError, reorderLevelError,
} from '../../lib/masterRules'
import { Fixed, FormFooter } from './formParts'

/**
 * R5.1, R5.2: create or edit an item. Code and kind are fixed once created (M3). The base unit has factor 1
 * (M8) and is locked once the item has moved or is in a recipe (M7): the backend sends the reason, shown here.
 * A finished good is counted in packets and has the fields an invoice and a carton order need (M9).
 */

// M8: a finished good is counted in packets; the others in a unit that smaller units convert into
const BASE_UNITS: Record<ItemKind, string[]> = {
  RAW: ['kg', 'L', 'pc'],
  PACKAGING: ['pc', 'kg', 'L'],
  FG: ['pkt'],
}

const KINDS: ItemKind[] = ['RAW', 'PACKAGING', 'FG']

/** Number from the API → the text in a field ("" for null). */
const text = (value: number | null) => (value === null ? '' : String(value))

export function ItemForm({ item, onSaved }: { item: Item | null; onSaved: (item: Item) => void }) {
  const save = useSaveItem(item?.code ?? null)
  const [code, setCode] = useState(item?.code ?? '')
  const [name, setName] = useState(item?.name ?? '')
  const [kind, setKind] = useState<ItemKind | ''>(item?.kind ?? '')
  const [baseUnit, setBaseUnit] = useState(item?.baseUnit ?? '')
  const [reorderLevel, setReorderLevel] = useState(text(item?.reorderLevel ?? null))
  const [hsnCode, setHsnCode] = useState(item?.hsnCode ?? '')
  const [gstRate, setGstRate] = useState(text(item?.gstRate ?? null))
  const [sellingPrice, setSellingPrice] = useState(text(item?.sellingPrice ?? null))
  const [packWeightKg, setPackWeightKg] = useState(text(item?.packWeightKg ?? null))
  const [packetsPerCarton, setPacketsPerCarton] = useState(text(item?.packetsPerCarton ?? null))
  const [showErrors, setShowErrors] = useState(false)

  const finished = kind === 'FG'
  const lockReason = item?.baseUnitLockReason ?? null
  const unitChoices = kind ? BASE_UNITS[kind] : []
  // An older item may have a base unit this form would not offer; keep it selectable
  const units = baseUnit && !unitChoices.includes(baseUnit) ? [baseUnit, ...unitChoices] : unitChoices

  const errors: Record<string, string | null> = {
    code: item ? null : codeError(code, 'item'),
    name: nameError(name),
    kind: kind ? null : 'Pick the kind: raw material, packaging or finished good.',
    baseUnit: baseUnit ? null : 'Pick the unit the item is counted in.',
    reorderLevel: reorderLevelError(reorderLevel),
    hsnCode: finished ? hsnError(hsnCode) : null,
    gstRate: finished ? gstRateError(gstRate) : null,
    sellingPrice: finished ? priceError(sellingPrice) : null,
    packWeightKg: finished ? packWeightError(packWeightKg) : null,
    packetsPerCarton: finished ? packetsPerCartonError(packetsPerCarton) : null,
  }
  const shown = (field: string) => (showErrors ? errors[field] ?? undefined : undefined)

  function chooseKind(next: ItemKind | '') {
    setKind(next)
    // The first unit the kind allows; a finished good can only be pkt
    setBaseUnit(next ? BASE_UNITS[next][0] : '')
  }

  function submit() {
    if (Object.values(errors).some(Boolean) || !kind) {
      setShowErrors(true)
      return
    }
    save.mutate(
      {
        code: item ? item.code : normaliseCode(code),
        name: name.trim(),
        kind,
        baseUnit,
        reorderLevel: optional(reorderLevel),
        // M9: only a finished good has these; for the others they must be empty
        hsnCode: finished ? hsnCode.trim() : null,
        gstRate: finished ? gstRate.trim() : null,
        sellingPrice: finished ? sellingPrice.trim() : null,
        packWeightKg: finished ? packWeightKg.trim() : null,
        packetsPerCarton: finished && packetsPerCarton.trim() ? Number(packetsPerCarton) : null,
      },
      { onSuccess: onSaved },
    )
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="grid max-w-3xl grid-cols-1 gap-4 sm:grid-cols-2">
        {item ? (
          <Fixed label="Code" value={item.code} />
        ) : (
          <Field label="Code" error={shown('code')} hint="Printed on documents. It cannot be changed later.">
            {({ id, describedBy }) => (
              <TextInput id={id} aria-describedby={describedBy} value={code} maxLength={30}
                onChange={(event) => setCode(event.target.value.toUpperCase())} />
            )}
          </Field>
        )}
        <Field label="Name" error={shown('name')}>
          {({ id, describedBy }) => (
            <TextInput id={id} aria-describedby={describedBy} value={name} maxLength={150} onChange={(event) => setName(event.target.value)} />
          )}
        </Field>
        {item ? (
          <Fixed label="Kind" value={ITEM_KIND_ONE[item.kind]} />
        ) : (
          <Field label="Kind" error={shown('kind')} hint="It cannot be changed later.">
            {({ id, describedBy }) => (
              <Select id={id} aria-describedby={describedBy} value={kind} onChange={(event) => chooseKind(event.target.value as ItemKind | '')}>
                <option value="">Pick a kind</option>
                {KINDS.map((option) => <option key={option} value={option}>{ITEM_KIND_ONE[option]}</option>)}
              </Select>
            )}
          </Field>
        )}
        {lockReason ? (
          <div className="flex flex-col gap-1">
            <p className="text-sm text-muted">Base unit</p>
            <p className="flex min-h-11 items-center gap-2 text-sm">
              <Lock size={16} aria-hidden="true" className="text-muted" />
              <span>{`${BASE_UNIT[baseUnit] ?? baseUnit}, locked`}</span>
            </p>
            <p className="text-[13px] leading-[18px] text-muted">{`${lockReason}.`}</p>
          </div>
        ) : (
          <Field label="Base unit" error={shown('baseUnit')}
            hint={finished ? 'Finished goods are counted in packets; cartons are set below.' : 'Stock, recipes and costs are kept in this unit. Smaller units such as g and ml convert into it.'}>
            {({ id, describedBy }) => (
              <Select id={id} aria-describedby={describedBy} value={baseUnit} disabled={!kind}
                onChange={(event) => setBaseUnit(event.target.value)}>
                {!kind ? <option value="">Pick the kind first</option> : null}
                {units.map((unit) => <option key={unit} value={unit}>{BASE_UNIT[unit] ?? unit}</option>)}
              </Select>
            )}
          </Field>
        )}
        <Field label={`Reorder level${baseUnit ? ` (${baseUnit})` : ''}`} error={shown('reorderLevel')} hint="Optional. The dashboard flags stock that falls below it.">
          {({ id, describedBy }) => (
            <TextInput id={id} aria-describedby={describedBy} inputMode="decimal" className="num" value={reorderLevel}
              onChange={(event) => setReorderLevel(event.target.value)} />
          )}
        </Field>
      </div>

      {finished ? (
        <fieldset className="max-w-3xl">
          <legend className="mb-3 text-base font-semibold">Finished-good details</legend>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Field label="HSN code" error={shown('hsnCode')}>
              {({ id, describedBy }) => (
                <TextInput id={id} aria-describedby={describedBy} inputMode="numeric" className="num" value={hsnCode} maxLength={8}
                  onChange={(event) => setHsnCode(event.target.value)} />
              )}
            </Field>
            <Field label="GST rate (%)" error={shown('gstRate')}>
              {({ id, describedBy }) => (
                <TextInput id={id} aria-describedby={describedBy} inputMode="decimal" className="num" value={gstRate}
                  onChange={(event) => setGstRate(event.target.value)} />
              )}
            </Field>
            <Field label="Selling price per packet (₹)" error={shown('sellingPrice')} hint="Before GST. A change applies to new orders only.">
              {({ id, describedBy }) => (
                <TextInput id={id} aria-describedby={describedBy} inputMode="decimal" className="num" value={sellingPrice}
                  onChange={(event) => setSellingPrice(event.target.value)} />
              )}
            </Field>
            <Field label="Pack weight (kg)" error={shown('packWeightKg')} hint="One packet: 0.5 for 500 g.">
              {({ id, describedBy }) => (
                <TextInput id={id} aria-describedby={describedBy} inputMode="decimal" className="num" value={packWeightKg}
                  onChange={(event) => setPackWeightKg(event.target.value)} />
              )}
            </Field>
            <Field label="Packets per carton" error={shown('packetsPerCarton')} hint="Optional. Empty means it cannot be ordered in cartons.">
              {({ id, describedBy }) => (
                <TextInput id={id} aria-describedby={describedBy} inputMode="numeric" className="num" value={packetsPerCarton}
                  onChange={(event) => setPacketsPerCarton(event.target.value)} />
              )}
            </Field>
          </div>
        </fieldset>
      ) : null}

      <FormFooter showErrors={showErrors} errors={errors} error={save.error} pending={save.isPending}
        label={item ? 'Save changes' : 'Create item'} onSubmit={submit} />
    </div>
  )
}
