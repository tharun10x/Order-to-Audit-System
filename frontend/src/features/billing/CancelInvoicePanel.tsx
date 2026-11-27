import { useState } from 'react'

import { useCancelInvoice } from '../../api/billing'
import type { Invoice } from '../../api/types'
import { Button } from '../../components/Button'
import { Field, TextArea } from '../../components/Field'
import { ErrorBox } from '../../components/Messages'
import { Panel } from '../../components/Panel'
import { formatDate, formatMoney, formatNumber } from '../../lib/format'

/**
 * R10.6, R10.7: a posted invoice is never deleted. Cancelling it raises a credit note for the full amount and, if it
 * was dispatched, brings the goods back into stock. The order is cancelled too. An invoice with payments cannot be
 * cancelled: the backend refuses and its reason is shown.
 */
export function CancelInvoicePanel({ invoice, onDone }: { invoice: Invoice; onDone: (notice: string) => void }) {
  const cancel = useCancelInvoice(invoice.id)
  const [reason, setReason] = useState('')
  const [missing, setMissing] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const packets = invoice.lines.reduce((sum, line) => sum + line.qty, 0)

  function ask() {
    if (!reason.trim()) {
      setMissing(true)
      return
    }
    setMissing(false)
    setConfirming(true)
  }

  function confirm() {
    cancel.mutate(reason.trim(), {
      onSuccess: (cancelled) => onDone(`Invoice cancelled. Credit note ${cancelled.creditNoteNo} created.`),
      onSettled: () => setConfirming(false),
    })
  }

  return (
    <Panel title="Cancel invoice">
      <div className="flex max-w-xl flex-col gap-3">
        <Field label="Reason" error={missing ? 'Enter a reason, for example "Wrong quantity billed".' : undefined}
          hint="Printed on the credit note.">
          {({ id, describedBy, invalid }) => (
            <TextArea id={id} value={reason} maxLength={500} aria-describedby={describedBy} aria-invalid={invalid}
              onChange={(event) => setReason(event.target.value)} />
          )}
        </Field>
        {confirming ? (
          <div role="alertdialog" aria-labelledby="cancel-what" className="rounded-control border border-danger p-4 text-sm">
            <p id="cancel-what" className="font-semibold">{`Cancel invoice ${invoice.invoiceNo}?`}</p>
            <ul className="mt-2 list-disc pl-5">
              <li>{`A credit note for ${formatMoney(invoice.total, { withSymbol: true })} is created, with the same tax split.`}</li>
              {invoice.dispatchNo ? (
                <li>{`The ${formatNumber(packets)} packets dispatched as ${invoice.dispatchNo} on ${formatDate(invoice.dispatchDate!)} return to stock.`}</li>
              ) : (
                <li>Nothing was dispatched, so stock does not change.</li>
              )}
              <li>{`The invoice and order ${invoice.orderNo} become cancelled. This cannot be undone.`}</li>
            </ul>
            <div className="mt-4 flex flex-wrap gap-3">
              <Button variant="danger" onClick={confirm} loading={cancel.isPending}>
                {`Yes, cancel ${invoice.invoiceNo}`}
              </Button>
              <Button onClick={() => setConfirming(false)} disabled={cancel.isPending}>
                Keep the invoice
              </Button>
            </div>
          </div>
        ) : (
          <div>
            <Button variant="danger" onClick={ask}>
              Cancel invoice
            </Button>
          </div>
        )}
        {cancel.error ? <ErrorBox error={cancel.error} /> : null}
      </div>
    </Panel>
  )
}
