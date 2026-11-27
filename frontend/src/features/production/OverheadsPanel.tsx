import { useState } from 'react'

import { useAddOverhead } from '../../api/operations'
import type { OverheadType, Run } from '../../api/types'
import { Button } from '../../components/Button'
import { Field, TextInput } from '../../components/Field'
import { Select } from '../../components/Layout'
import { ErrorBox, SuccessLine } from '../../components/Messages'
import { Panel } from '../../components/Panel'
import { Table, Td, Th } from '../../components/Table'
import { isAmount } from '../../lib/decimal'
import { formatMoney } from '../../lib/format'
import { OVERHEAD_TYPE } from '../../lib/labels'

/** R13.3: labour, gas/power and other costs of a run, on a DRAFT or POSTED run. They never enter stock value. */
export function OverheadsPanel({ run, canAdd }: { run: Run; canAdd: boolean }) {
  const add = useAddOverhead(run.id)
  const [type, setType] = useState<OverheadType>('LABOUR')
  const [amount, setAmount] = useState('')
  const [note, setNote] = useState('')
  const [showError, setShowError] = useState(false)
  const [added, setAdded] = useState<string | null>(null)
  const amountOk = isAmount(amount) && Number(amount) > 0

  function submit() {
    if (!amountOk) {
      setShowError(true)
      return
    }
    add.mutate({ type, amount: amount.trim(), note: note.trim() || null }, {
      onSuccess: () => {
        setAdded(`${OVERHEAD_TYPE[type]} of ₹${amount.trim()} added.`)
        setAmount('')
        setNote('')
        setShowError(false)
      },
    })
  }

  return (
    <Panel title="Overheads">
      {run.overheads.length > 0 ? (
        <Table label="Overheads">
          <thead>
            <tr>
              <Th>Type</Th>
              <Th>Note</Th>
              <Th numeric>Amount (₹)</Th>
            </tr>
          </thead>
          <tbody>
            {run.overheads.map((overhead) => (
              <tr key={overhead.id}>
                <Td>{OVERHEAD_TYPE[overhead.type]}</Td>
                <Td>{overhead.note ?? ''}</Td>
                <Td numeric>{formatMoney(overhead.amount)}</Td>
              </tr>
            ))}
          </tbody>
        </Table>
      ) : (
        <p className="text-sm text-muted">No overheads on this run yet.</p>
      )}
      {canAdd ? (
        <div className="mt-6 flex flex-col gap-3">
          {added ? <SuccessLine>{added}</SuccessLine> : null}
          <div className="grid max-w-2xl grid-cols-1 gap-4 sm:grid-cols-3">
            <Field label="Type">
              {({ id }) => (
                <Select id={id} value={type} onChange={(event) => setType(event.target.value as OverheadType)}>
                  {(Object.keys(OVERHEAD_TYPE) as OverheadType[]).map((value) => (
                    <option key={value} value={value}>{OVERHEAD_TYPE[value]}</option>
                  ))}
                </Select>
              )}
            </Field>
            <Field label="Amount (₹)" error={showError && !amountOk ? 'Enter an amount above 0, like 1200 or 1200.50.' : undefined}>
              {({ id, describedBy, invalid }) => (
                <TextInput id={id} inputMode="decimal" value={amount} className="num" aria-describedby={describedBy}
                  aria-invalid={invalid} onChange={(event) => setAmount(event.target.value)} />
              )}
            </Field>
            <Field label="Note (optional)">
              {({ id }) => <TextInput id={id} value={note} maxLength={200} onChange={(event) => setNote(event.target.value)} />}
            </Field>
          </div>
          {add.error ? <ErrorBox error={add.error} /> : null}
          <div>
            <Button onClick={submit} loading={add.isPending}>Add overhead</Button>
          </div>
        </div>
      ) : null}
    </Panel>
  )
}
