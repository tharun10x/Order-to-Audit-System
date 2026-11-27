import { useState } from 'react'
import { Link, useNavigate } from 'react-router'

import { useCreateRun, useItems } from '../../api/operations'
import { useSession } from '../../auth/authContext'
import { Button } from '../../components/Button'
import { Field, TextInput } from '../../components/Field'
import { PageHeader, Select } from '../../components/Layout'
import { ErrorBox } from '../../components/Messages'
import { Panel } from '../../components/Panel'
import { isQuantity } from '../../lib/decimal'

/**
 * R8.4, R8.5: a new run copies the product's active recipe as its standard. It starts as a DRAFT that moves no
 * stock; the run page shows the planned quantities and takes the actuals.
 */
export function NewRunPage() {
  const { businessDate } = useSession()
  const items = useItems()
  const create = useCreateRun()
  const navigate = useNavigate()
  const [productCode, setProductCode] = useState('')
  const [runDate, setRunDate] = useState(businessDate)
  const [batches, setBatches] = useState('1')
  const [showErrors, setShowErrors] = useState(false)

  const products = (items.data ?? []).filter((item) => item.kind === 'FG' && item.hasActiveBom)
  const errors = {
    product: productCode ? null : 'Pick the product.',
    date: runDate ? null : 'Pick the run date.',
    batches: isQuantity(batches) && Number(batches) > 0 ? null : 'Enter the number of batches, for example 1 or 1.5.',
  }

  function submit() {
    if (Object.values(errors).some(Boolean)) {
      setShowErrors(true)
      return
    }
    create.mutate({ productCode, runDate, batches: batches.trim() }, { onSuccess: (run) => navigate(`/production/runs/${run.id}`) })
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="New production run">
        <p className="mt-1 text-sm"><Link to="/production/runs" className="text-primary hover:underline">All runs</Link></p>
      </PageHeader>
      <Panel>
        {items.error ? <ErrorBox error={items.error} /> : null}
        <div className="grid max-w-2xl grid-cols-1 gap-4 sm:grid-cols-3">
          <Field label="Product" error={showErrors ? errors.product ?? undefined : undefined}>
            {({ id }) => (
              <Select id={id} value={productCode} onChange={(event) => setProductCode(event.target.value)}>
                <option value="">Pick a product</option>
                {products.map((product) => <option key={product.code} value={product.code}>{product.name}</option>)}
              </Select>
            )}
          </Field>
          <Field label="Run date" error={showErrors ? errors.date ?? undefined : undefined}>
            {({ id }) => <TextInput id={id} type="date" value={runDate} onChange={(event) => setRunDate(event.target.value)} />}
          </Field>
          <Field label="Batches" error={showErrors ? errors.batches ?? undefined : undefined}>
            {({ id }) => (
              <TextInput id={id} inputMode="decimal" value={batches} className="num" onChange={(event) => setBatches(event.target.value)} />
            )}
          </Field>
        </div>
        <div className="mt-6 flex flex-col gap-3">
          {create.error ? <ErrorBox error={create.error} /> : null}
          <div>
            <Button variant="primary" onClick={submit} loading={create.isPending}>Create draft run</Button>
          </div>
          <p className="text-[13px] leading-[18px] text-muted">A draft moves no stock. Stock moves when the run is posted.</p>
        </div>
      </Panel>
    </div>
  )
}
