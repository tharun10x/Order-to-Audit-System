import { useState } from 'react'

import { useCreateInvoice } from '../../api/queries'
import type { Order } from '../../api/types'
import { useSession } from '../../auth/authContext'
import { Button } from '../../components/Button'
import { Field, TextInput } from '../../components/Field'
import { ErrorBox } from '../../components/Messages'
import { Panel } from '../../components/Panel'

/**
 * R10.1: an approved order becomes one GST invoice. The date defaults to the business date (A17); the invoice
 * screens themselves come in milestone 2.
 */
export function CreateInvoicePanel({ order, onDone }: { order: Order; onDone: (notice: string) => void }) {
  const session = useSession()
  const createInvoice = useCreateInvoice()
  const [invoiceDate, setInvoiceDate] = useState(session.businessDate)

  return (
    <Panel title="Create invoice">
      <div className="flex max-w-xs flex-col gap-3">
        <Field label="Invoice date" error={invoiceDate ? undefined : 'Pick the invoice date.'}>
          {({ id, describedBy, invalid }) => (
            <TextInput
              id={id}
              type="date"
              value={invoiceDate}
              onChange={(event) => setInvoiceDate(event.target.value)}
              aria-describedby={describedBy}
              aria-invalid={invalid}
            />
          )}
        </Field>
        {createInvoice.error ? <ErrorBox error={createInvoice.error} /> : null}
        <div>
          <Button
            variant="primary"
            disabled={!invoiceDate}
            loading={createInvoice.isPending}
            onClick={() =>
              createInvoice.mutate(
                { orderId: order.id, invoiceDate },
                { onSuccess: (invoice) => onDone(`Invoice ${invoice.invoiceNo} created.`) },
              )
            }
          >
            Create invoice
          </Button>
        </div>
      </div>
    </Panel>
  )
}
