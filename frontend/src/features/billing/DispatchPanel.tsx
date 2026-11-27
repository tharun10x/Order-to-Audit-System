import { useState } from 'react'

import { useDispatch } from '../../api/billing'
import type { Invoice } from '../../api/types'
import { useSession } from '../../auth/authContext'
import { Button } from '../../components/Button'
import { Field, TextInput } from '../../components/Field'
import { ErrorBox } from '../../components/Messages'
import { formatNumber } from '../../lib/format'

/**
 * R11: dispatch the whole invoice. This is when finished goods leave stock; the backend checks stock again under
 * lock and refuses if it is short. The date defaults to the business date.
 */
export function DispatchPanel({ invoice, onDone }: { invoice: Invoice; onDone: (notice: string) => void }) {
  const { businessDate } = useSession()
  const dispatch = useDispatch()
  const [dispatchDate, setDispatchDate] = useState(businessDate)
  const [vehicleNo, setVehicleNo] = useState('')
  const packets = invoice.lines.reduce((sum, line) => sum + line.qty, 0)

  return (
    <div className="flex max-w-md flex-col gap-3">
      <p className="text-sm">{`Not dispatched yet. Dispatch sends ${formatNumber(packets)} packets out of stock.`}</p>
      <Field label="Dispatch date" error={dispatchDate ? undefined : 'Pick the dispatch date.'}>
        {({ id, describedBy }) => (
          <TextInput id={id} type="date" value={dispatchDate} aria-describedby={describedBy}
            onChange={(event) => setDispatchDate(event.target.value)} />
        )}
      </Field>
      <Field label="Vehicle number (optional)">
        {({ id }) => (
          <TextInput id={id} value={vehicleNo} maxLength={20} onChange={(event) => setVehicleNo(event.target.value)} />
        )}
      </Field>
      {dispatch.error ? <ErrorBox error={dispatch.error} /> : null}
      <div>
        <Button
          variant="primary"
          disabled={!dispatchDate}
          loading={dispatch.isPending}
          onClick={() =>
            dispatch.mutate(
              { invoiceId: invoice.id, dispatchDate, vehicleNo: vehicleNo.trim() || null },
              { onSuccess: (result) => onDone(`Dispatched as ${result.dispatchNo}.`) },
            )
          }
        >
          Record dispatch
        </Button>
      </div>
    </div>
  )
}
