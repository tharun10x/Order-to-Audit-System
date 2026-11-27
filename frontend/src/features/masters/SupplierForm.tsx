import { useState } from 'react'

import { useSaveSupplier } from '../../api/masters'
import type { Supplier } from '../../api/types'
import { Field, TextInput } from '../../components/Field'
import { gstinError, nameError, optional } from '../../lib/masterRules'
import { FormFooter, GstinField, StateField } from './formParts'

/** R5.3: create or edit a supplier. Like a customer, without code or salesman; the state is optional (M4). */
export function SupplierForm({ supplier, onSaved }: { supplier: Supplier | null; onSaved: (supplier: Supplier) => void }) {
  const save = useSaveSupplier(supplier?.id ?? null)
  const [name, setName] = useState(supplier?.name ?? '')
  const [stateCode, setStateCode] = useState(supplier?.stateCode ?? '')
  const [gstin, setGstin] = useState(supplier?.gstin ?? '')
  const [phone, setPhone] = useState(supplier?.phone ?? '')
  const [showErrors, setShowErrors] = useState(false)

  const errors: Record<string, string | null> = { name: nameError(name), gstin: gstinError(gstin, stateCode) }
  const shown = (field: string) => (showErrors ? errors[field] ?? undefined : undefined)

  function submit() {
    if (Object.values(errors).some(Boolean)) {
      setShowErrors(true)
      return
    }
    save.mutate(
      { name: name.trim(), stateCode: optional(stateCode), gstin: optional(gstin.toUpperCase()), phone: optional(phone) },
      { onSuccess: onSaved },
    )
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="grid max-w-3xl grid-cols-1 gap-4 sm:grid-cols-2">
        <Field label="Name" error={shown('name')}>
          {({ id, describedBy }) => (
            <TextInput id={id} aria-describedby={describedBy} value={name} maxLength={150} onChange={(event) => setName(event.target.value)} />
          )}
        </Field>
        <StateField value={stateCode} onChange={setStateCode} optional />
        <GstinField value={gstin} onChange={setGstin} error={shown('gstin')} />
        <Field label="Phone">
          {({ id }) => <TextInput id={id} type="tel" value={phone} maxLength={15} onChange={(event) => setPhone(event.target.value)} />}
        </Field>
      </div>
      <FormFooter showErrors={showErrors} errors={errors} error={save.error} pending={save.isPending}
        label={supplier ? 'Save changes' : 'Create supplier'} onSubmit={submit} />
    </div>
  )
}
