import type { ReactNode } from 'react'

import { Button } from '../../components/Button'
import { Field, TextInput } from '../../components/Field'
import { Select } from '../../components/Layout'
import { ErrorBox } from '../../components/Messages'
import { GST_STATES } from '../../lib/gstStates'

/** Pieces the item, customer and supplier forms share. */

/** A value that can no longer change, shown where its input would be. */
export function Fixed({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-col gap-1">
      <p className="text-sm text-muted">{label}</p>
      <p className="flex min-h-11 items-center text-sm">{value}</p>
    </div>
  )
}

/** R5.3: a real GST state, picked by name. optional: suppliers may leave it empty (M4). */
export function StateField({ value, onChange, error, optional = false }: {
  value: string
  onChange: (value: string) => void
  error?: string
  optional?: boolean
}) {
  return (
    <Field label="State" error={error} hint={optional ? 'Optional, but needed with a GSTIN.' : undefined}>
      {({ id, describedBy }) => (
        <Select id={id} aria-describedby={describedBy} value={value} onChange={(event) => onChange(event.target.value)}>
          <option value="">{optional ? 'Not given' : 'Pick a state'}</option>
          {GST_STATES.map((state) => <option key={state.code} value={state.code}>{`${state.name} (${state.code})`}</option>)}
        </Select>
      )}
    </Field>
  )
}

/** M1: optional, 15 characters, saved in capitals. */
export function GstinField({ value, onChange, error }: { value: string; onChange: (value: string) => void; error?: string }) {
  return (
    <Field label="GSTIN" error={error} hint="Optional. 15 characters, starting with the state code.">
      {({ id, describedBy }) => (
        <TextInput id={id} aria-describedby={describedBy} value={value} maxLength={15} className="uppercase"
          onChange={(event) => onChange(event.target.value.toUpperCase())} />
      )}
    </Field>
  )
}

/** The summary line when fields are wrong, the server's refusal, the one primary button and an optional second. */
export function FormFooter({ showErrors, errors, error, pending, label, onSubmit, secondary }: {
  showErrors: boolean
  errors: Record<string, string | null>
  error: unknown
  pending: boolean
  label: string
  onSubmit: () => void
  secondary?: ReactNode
}) {
  return (
    <div className="flex flex-col gap-3">
      {showErrors && Object.values(errors).some(Boolean) ? (
        <ErrorBox message="Some values need correcting. Each one is marked above." />
      ) : null}
      {error ? <ErrorBox error={error} /> : null}
      <div className="flex flex-wrap gap-3">
        <Button variant="primary" onClick={onSubmit} loading={pending}>{label}</Button>
        {secondary}
      </div>
    </div>
  )
}
