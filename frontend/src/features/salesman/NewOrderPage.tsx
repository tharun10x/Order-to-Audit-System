import { useState } from 'react'
import { Link } from 'react-router'

import { ApiError } from '../../api/client'
import { useCreateOrder, useCustomers, useProducts } from '../../api/queries'
import type { Customer, Order, Product, Unit } from '../../api/types'
import { Button } from '../../components/Button'
import { EmptyState, ErrorBox, SuccessLine } from '../../components/Messages'
import { SegmentedToggle } from '../../components/SegmentedToggle'
import { Stepper } from '../../components/Stepper'
import { formatMoney, formatQty } from '../../lib/format'
import { estimateOrder, type Estimate } from '../../lib/gstEstimate'
import { changeDraft, loadDraft, newDraft, saveDraft, type OrderDraft } from './orderDraft'

/**
 * Brief 4.1 and section 9: a salesman places an order in under a minute. Pick the customer, set the quantity,
 * check the estimate, submit. The draft survives a reload, and resending after a dropped connection reuses the
 * same client UUID, so the factory never gets the order twice (R9.7).
 */
export function NewOrderPage() {
  const customers = useCustomers()
  const products = useProducts()
  const createOrder = useCreateOrder()
  const [draft, setDraft] = useState<OrderDraft>(loadDraft)
  const [sent, setSent] = useState<Order | null>(null)

  function update(next: OrderDraft) {
    saveDraft(next)
    setDraft(next)
    setSent(null)
    createOrder.reset()
  }

  const customer = customers.data?.find((candidate) => candidate.id === draft.customerId) ?? null
  const lines = (products.data ?? [])
    .map((product) => ({ product, entry: draft.quantities[product.code] ?? { qty: 0, unit: 'pkt' as Unit } }))
    .filter(({ entry }) => entry.qty > 0)
  const canSubmit = customer !== null && lines.length > 0
  const networkFailed = createOrder.error instanceof ApiError && createOrder.error.isNetwork

  function submit() {
    if (!customer) {
      return
    }
    createOrder.mutate(
      {
        customerId: customer.id,
        clientUuid: draft.clientUuid,
        lines: lines.map(({ product, entry }) => ({ itemCode: product.code, qty: entry.qty, uomCode: entry.unit })),
      },
      {
        onSuccess: (order) => {
          const fresh = newDraft()
          saveDraft(fresh)
          setDraft(fresh)
          setSent(order)
        },
      },
    )
  }

  if (customers.error || products.error) {
    return <ErrorBox error={customers.error ?? products.error} />
  }
  if (!customers.data || !products.data) {
    return <p className="text-muted">Loading…</p>
  }

  return (
    <div className="pb-44">
      <h1 className="mb-4 text-xl leading-7 font-semibold">New order</h1>
      {sent ? (
        <div className="mb-4 flex flex-col gap-1">
          <SuccessLine>{`Order ${sent.orderNo} sent to the factory.`}</SuccessLine>
          <Link to="/sales/orders" className="min-h-13 content-center text-base text-primary underline">
            See my orders
          </Link>
        </div>
      ) : null}

      <CustomerSection
        customers={customers.data}
        selected={customer}
        onSelect={(id) => update(changeDraft(draft, { customerId: id }))}
      />

      {customer ? (
        <>
          {products.data.map((product) => (
            <ProductCard
              key={product.code}
              product={product}
              qty={draft.quantities[product.code]?.qty ?? 0}
              unit={draft.quantities[product.code]?.unit ?? 'pkt'}
              onChange={(qty, unit) =>
                update(changeDraft(draft, { quantities: { ...draft.quantities, [product.code]: { qty, unit } } }))
              }
            />
          ))}
          <RunningTotal
            intraState={customer.intraState}
            estimate={estimateOrder(
              lines.map(({ product, entry }) => ({
                packets: entry.unit === 'ctn' ? entry.qty * (product.packetsPerCarton ?? 0) : entry.qty,
                rate: product.sellingPrice,
                gstRate: product.gstRate,
              })),
              customer.intraState,
            )}
          />
        </>
      ) : null}

      <div className="fixed inset-x-0 bottom-0 border-t border-border bg-surface">
        <div className="mx-auto flex max-w-[480px] flex-col gap-2 px-4 py-3">
          {createOrder.error ? (
            <ErrorBox
              error={createOrder.error}
              message={networkFailed ? 'Not sent: no connection. Your order is kept on this phone; send it again.' : undefined}
            />
          ) : null}
          {!canSubmit ? (
            <p className="text-[13px] leading-[18px] text-muted">
              {customer ? 'Enter a quantity to submit.' : 'Pick a customer to start.'}
            </p>
          ) : null}
          <Button variant="primary" size="xl" onClick={submit} disabled={!canSubmit} loading={createOrder.isPending}>
            {networkFailed ? 'Submit order again' : 'Submit order'}
          </Button>
          <p className="text-[13px] leading-[18px] text-muted">
            If the network drops, the order stays on this phone until it is sent.
          </p>
        </div>
      </div>
    </div>
  )
}

function CustomerSection({ customers, selected, onSelect }: {
  customers: Customer[]
  selected: Customer | null
  onSelect: (id: number | null) => void
}) {
  if (selected) {
    return (
      <div className="mb-5 flex items-center justify-between gap-3 border-b border-rule pb-4">
        <div>
          <p className="text-[13px] leading-[18px] text-muted">Customer</p>
          <p className="text-lg leading-[26px] font-semibold">{selected.name}</p>
          <p className="text-[13px] leading-[18px] text-muted">{selected.intraState ? 'Tamil Nadu · CGST + SGST' : 'Other state · IGST'}</p>
        </div>
        <Button size="lg" onClick={() => onSelect(null)}>
          Change customer
        </Button>
      </div>
    )
  }
  if (customers.length === 0) {
    return <EmptyState>No customers are assigned to you yet. Ask the admin to assign your customers.</EmptyState>
  }
  return (
    <section aria-labelledby="pick-customer" className="mb-5">
      <h2 id="pick-customer" className="mb-2 text-lg leading-[26px] font-semibold">
        Pick a customer
      </h2>
      <ul className="flex flex-col gap-2">
        {customers.map((customer) => (
          <li key={customer.id}>
            <button
              type="button"
              onClick={() => onSelect(customer.id)}
              className="flex min-h-13 w-full flex-col items-start justify-center rounded-control border border-border bg-surface px-4 py-2 text-left hover:border-primary"
            >
              <span className="font-medium">{customer.name}</span>
              <span className="text-[13px] leading-[18px] text-muted">{customer.address ?? customer.code}</span>
            </button>
          </li>
        ))}
      </ul>
    </section>
  )
}

function ProductCard({ product, qty, unit, onChange }: {
  product: Product
  qty: number
  unit: Unit
  onChange: (qty: number, unit: Unit) => void
}) {
  const perCarton = product.packetsPerCarton
  const packets = unit === 'ctn' && perCarton ? qty * perCarton : qty
  return (
    <section aria-label={product.name} className="mb-5 border-b border-rule pb-5">
      <div className="mb-3 flex items-baseline justify-between gap-3">
        <h2 className="text-lg leading-[26px] font-semibold">{product.name}</h2>
        <span className="num text-[13px] leading-[18px] text-muted">{`${formatMoney(product.sellingPrice, { withSymbol: true })} per packet`}</span>
      </div>
      {perCarton ? (
        <div className="mb-3">
          <SegmentedToggle
            label="Order in"
            value={unit}
            onChange={(next) => onChange(qty, next)}
            options={[
              { value: 'pkt', label: 'Packets' },
              { value: 'ctn', label: 'Cartons' },
            ]}
          />
        </div>
      ) : null}
      <Stepper
        label={unit === 'ctn' ? 'Cartons' : 'Packets'}
        unitLabel={unit === 'ctn' ? 'carton' : 'packet'}
        value={qty}
        onChange={(next) => onChange(next, unit)}
      />
      {unit === 'ctn' && perCarton ? (
        <p className="num mt-2 text-[13px] leading-[18px] text-muted">{`${formatQty(qty, 'ctn')} = ${formatQty(packets, 'pkt')} (${perCarton} per carton)`}</p>
      ) : null}
    </section>
  )
}

function RunningTotal({ estimate, intraState }: { estimate: Estimate; intraState: boolean }) {
  const row = (label: string, amount: number, strong = false) => (
    <div className={`flex justify-between py-1 ${strong ? 'border-t border-rule pt-2 text-lg leading-[26px] font-semibold' : ''}`}>
      <dt>{label}</dt>
      <dd className="num">{formatMoney(amount, { withSymbol: true })}</dd>
    </div>
  )
  return (
    <section aria-label="Estimated total" className="mb-4">
      <dl>
        {row('Taxable value', estimate.taxable)}
        {intraState ? (
          <>
            {row('CGST', estimate.cgst)}
            {row('SGST', estimate.sgst)}
          </>
        ) : (
          row('IGST', estimate.igst)
        )}
        {row('Total', estimate.total, true)}
      </dl>
      <p className="mt-1 text-[13px] leading-[18px] text-muted">Estimate. The invoice is final.</p>
    </section>
  )
}
