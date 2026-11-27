import { useState } from 'react'

import { useSalesmen, useSaveCustomer } from '../../api/masters'
import type { Customer, CustomerType } from '../../api/types'
import { Field, TextArea, TextInput } from '../../components/Field'
import { Select } from '../../components/Layout'
import { ErrorBox } from '../../components/Messages'
import { CUSTOMER_TYPE } from '../../lib/labels'
import { codeError, gstinError, nameError, normaliseCode, optional } from '../../lib/masterRules'
import { Fixed, FormFooter, GstinField, StateField } from './formParts'

/**
 * R5.3: create or edit a customer. The code is fixed once created (M3). The state decides CGST + SGST or IGST on
 * the invoice (R10.3), and a GSTIN must start with it (M1). Every customer is looked after by an active
 * salesman (M5), who then sees it on the phone (R2.2).
 */
export function CustomerForm({ customer, onSaved }: { customer: Customer | null; onSaved: (customer: Customer) => void }) {
  const save = useSaveCustomer(customer?.id ?? null)
  const salesmen = useSalesmen()
  const [code, setCode] = useState(customer?.code ?? '')
  const [name, setName] = useState(customer?.name ?? '')
  const [type, setType] = useState<CustomerType | ''>(customer?.type ?? '')
  const [stateCode, setStateCode] = useState(customer?.stateCode ?? '')
  const [gstin, setGstin] = useState(customer?.gstin ?? '')
  const [address, setAddress] = useState(customer?.address ?? '')
  const [phone, setPhone] = useState(customer?.phone ?? '')
  const [salesmanId, setSalesmanId] = useState(customer?.salesmanId ? String(customer.salesmanId) : '')
  const [showErrors, setShowErrors] = useState(false)

  // A salesman who has since left is not in the list: the admin has to pick someone who is
  const salesmanGone = Boolean(salesmen.data && salesmanId && !salesmen.data.some((s) => String(s.id) === salesmanId))
  const errors: Record<string, string | null> = {
    code: customer ? null : codeError(code, 'customer'),
    name: nameError(name),
    type: type ? null : 'Pick retailer or distributor.',
    stateCode: stateCode ? null : 'Pick the state: it decides CGST + SGST or IGST on the invoice.',
    gstin: gstinError(gstin, stateCode),
    salesmanId: salesmanId && !salesmanGone
      ? null
      : salesmanGone
        ? `${customer?.salesmanName ?? 'The salesman'} is no longer an active salesman. Pick another.`
        : 'Pick the salesman who looks after this customer.',
  }
  const shown = (field: string) => (showErrors ? errors[field] ?? undefined : undefined)

  function submit() {
    if (Object.values(errors).some(Boolean) || !type) {
      setShowErrors(true)
      return
    }
    save.mutate(
      {
        code: customer ? customer.code : normaliseCode(code),
        name: name.trim(),
        type,
        stateCode,
        gstin: optional(gstin.toUpperCase()),
        address: optional(address),
        phone: optional(phone),
        salesmanId: Number(salesmanId),
      },
      { onSuccess: onSaved },
    )
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="grid max-w-3xl grid-cols-1 gap-4 sm:grid-cols-2">
        {customer ? (
          <Fixed label="Code" value={customer.code} />
        ) : (
          <Field label="Code" error={shown('code')} hint="It cannot be changed later.">
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
        <Field label="Type" error={shown('type')}>
          {({ id, describedBy }) => (
            <Select id={id} aria-describedby={describedBy} value={type} onChange={(event) => setType(event.target.value as CustomerType | '')}>
              <option value="">Pick a type</option>
              {(Object.keys(CUSTOMER_TYPE) as CustomerType[]).map((option) => (
                <option key={option} value={option}>{CUSTOMER_TYPE[option]}</option>
              ))}
            </Select>
          )}
        </Field>
        <Field label="Salesman" error={shown('salesmanId')}>
          {({ id, describedBy }) => (
            <Select id={id} aria-describedby={describedBy} value={salesmanGone ? '' : salesmanId}
              onChange={(event) => setSalesmanId(event.target.value)} disabled={!salesmen.data}>
              <option value="">{salesmen.data ? 'Pick a salesman' : 'Loading salesmen…'}</option>
              {(salesmen.data ?? []).map((salesman) => (
                <option key={salesman.id} value={salesman.id}>{salesman.fullName}</option>
              ))}
            </Select>
          )}
        </Field>
        <StateField value={stateCode} onChange={setStateCode} error={shown('stateCode')} />
        <GstinField value={gstin} onChange={setGstin} error={shown('gstin')} />
        <Field label="Phone">
          {({ id }) => <TextInput id={id} type="tel" value={phone} maxLength={15} onChange={(event) => setPhone(event.target.value)} />}
        </Field>
        <div className="sm:col-span-2">
          <Field label="Address" hint="Printed on the invoice.">
            {({ id, describedBy }) => (
              <TextArea id={id} aria-describedby={describedBy} value={address} maxLength={500} onChange={(event) => setAddress(event.target.value)} />
            )}
          </Field>
        </div>
      </div>
      {salesmen.error ? <ErrorBox error={salesmen.error} /> : null}
      <FormFooter showErrors={showErrors} errors={errors} error={save.error} pending={save.isPending}
        label={customer ? 'Save changes' : 'Create customer'} onSubmit={submit} />
    </div>
  )
}
