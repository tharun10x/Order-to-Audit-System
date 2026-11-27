import { useState } from 'react'

import { useRejectOrder } from '../../api/queries'
import type { Order } from '../../api/types'
import { Button } from '../../components/Button'
import { Field, TextArea } from '../../components/Field'
import { ErrorBox } from '../../components/Messages'
import { Panel } from '../../components/Panel'

/** R9.9: a rejection needs a reason; the salesman sees it on their order. */
export function RejectPanel({ order, onDone }: { order: Order; onDone: (notice: string) => void }) {
  const reject = useRejectOrder(order.id)
  const [reason, setReason] = useState('')
  const [missing, setMissing] = useState(false)

  function submit() {
    if (!reason.trim()) {
      setMissing(true)
      return
    }
    setMissing(false)
    reject.mutate(reason.trim(), { onSuccess: () => onDone('Order rejected.') })
  }

  return (
    <Panel title="Reject order">
      <div className="flex max-w-xl flex-col gap-3">
        <Field
          label="Reason"
          error={missing ? 'Enter a reason, for example "Credit limit reached".' : undefined}
          hint="The salesman sees this reason."
        >
          {({ id, describedBy, invalid }) => (
            <TextArea
              id={id}
              value={reason}
              maxLength={500}
              onChange={(event) => setReason(event.target.value)}
              aria-describedby={describedBy}
              aria-invalid={invalid}
            />
          )}
        </Field>
        {reject.error ? <ErrorBox error={reject.error} /> : null}
        <div>
          <Button variant="danger" onClick={submit} loading={reject.isPending}>
            Reject order
          </Button>
        </div>
      </div>
    </Panel>
  )
}
