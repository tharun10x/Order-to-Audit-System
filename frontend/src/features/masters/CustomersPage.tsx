import { useState, type ReactNode } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'

import { useMasterCustomers, useSetCustomerActive } from '../../api/masters'
import type { Customer } from '../../api/types'
import { useSession } from '../../auth/authContext'
import { can } from '../../auth/permissions'
import { Facts, LinkButton } from '../../components/Layout'
import { EmptyState, ErrorBox, SuccessLine } from '../../components/Messages'
import { Panel } from '../../components/Panel'
import { Table, Td, Th } from '../../components/Table'
import { describeState } from '../../lib/gstStates'
import { CUSTOMER_TYPE } from '../../lib/labels'
import { ActiveToggle } from './ActiveToggle'
import { CustomerForm } from './CustomerForm'
import { useMasterStatus, useStatusQuery } from './masterStatus'
import { ActiveWord, StatusFilter } from './StatusFilter'

/** R5.3: customers, then the selected one below: a form for the admin, its details for the auditor and owner. */
export function CustomersPage({ creating = false }: { creating?: boolean }) {
  const session = useSession()
  const manage = can.manageMasters(session.user.role)
  const status = useMasterStatus()
  const query = useStatusQuery()
  const { customerId } = useParams()
  const navigate = useNavigate()
  const notice = (useLocation().state as { notice?: string } | null)?.notice ?? null
  const customers = useMasterCustomers(status)
  // The selected customer is looked up in all of them, so changing the filter (or deactivating) keeps it open
  const everyCustomer = useMasterCustomers('all')
  const id = customerId ? Number(customerId) : null
  const selected = id ? everyCustomer.data?.find((customer) => customer.id === id) : undefined

  return (
    <>
      <Panel title="Customers">
        <div className="mb-4 flex flex-wrap items-end justify-between gap-4">
          <StatusFilter />
          {manage ? <LinkButton variant="primary" to={`/masters/customers/new${query}`}>New customer</LinkButton> : null}
        </div>
        {customers.error ? <ErrorBox error={customers.error} /> : null}
        {customers.data && customers.data.length === 0 ? (
          <EmptyState>{status === 'inactive' ? 'No inactive customers.' : 'No customers yet. Customers created here can be picked by their salesman.'}</EmptyState>
        ) : null}
        {customers.data && customers.data.length > 0 ? (
          <Table label="Customers">
            <thead>
              <tr>
                <Th>Code</Th>
                <Th>Name</Th>
                <Th>Type</Th>
                <Th>State</Th>
                <Th>GSTIN</Th>
                <Th>Salesman</Th>
                <Th>Status</Th>
              </tr>
            </thead>
            <tbody>
              {customers.data.map((customer) => (
                <tr key={customer.id} className={customer.id === id ? 'bg-primary-tint' : undefined}>
                  <Td className="whitespace-nowrap">
                    <Link to={`/masters/customers/${customer.id}${query}`} className="font-medium text-primary hover:underline"
                      aria-current={customer.id === id ? 'true' : undefined}>
                      {customer.code}
                    </Link>
                  </Td>
                  <Td>{customer.name}</Td>
                  <Td>{CUSTOMER_TYPE[customer.type]}</Td>
                  <Td>{describeState(customer.stateCode)}</Td>
                  <Td className="num">{customer.gstin ?? ''}</Td>
                  <Td>{customer.salesmanName ?? 'Nobody yet'}</Td>
                  <Td><ActiveWord active={customer.active} /></Td>
                </tr>
              ))}
            </tbody>
          </Table>
        ) : null}
      </Panel>

      {creating && manage ? (
        <Panel title="New customer">
          <CustomerForm
            customer={null}
            onSaved={(customer) => navigate(`/masters/customers/${customer.id}${query}`, {
              state: { notice: `Customer ${customer.code} created. ${customer.salesmanName ?? 'The salesman'} can now take orders for it.` },
            })}
          />
        </Panel>
      ) : null}
      {id && everyCustomer.data && !selected ? <ErrorBox message="This customer does not exist." /> : null}
      {selected ? <CustomerDetail key={selected.id} customer={selected} manage={manage} initialNotice={notice} /> : null}
    </>
  )
}

function CustomerDetail({ customer, manage, initialNotice }: { customer: Customer; manage: boolean; initialNotice: string | null }) {
  const [notice, setNotice] = useState(initialNotice)
  // A new key after each save starts the form again from the saved values
  const [version, setVersion] = useState(0)
  const setActive = useSetCustomerActive(customer.id)

  if (!manage) {
    const facts: [string, ReactNode][] = [
      ['Code', customer.code],
      ['Type', CUSTOMER_TYPE[customer.type]],
      ['State', describeState(customer.stateCode)],
      ['GST on invoices', customer.intraState ? 'CGST + SGST' : 'IGST'],
      ['GSTIN', customer.gstin ?? 'Not registered'],
      ['Salesman', customer.salesmanName ?? 'Nobody yet'],
      ['Phone', customer.phone ?? ''],
      ['Address', customer.address ?? ''],
      ['Status', <ActiveWord key="status" active={customer.active} />],
    ]
    return <Panel title={customer.name}><Facts items={facts} /></Panel>
  }
  return (
    <>
      <Panel title={`Edit ${customer.name}`}>
        {notice ? <div className="mb-4"><SuccessLine>{notice}</SuccessLine></div> : null}
        <CustomerForm key={version} customer={customer} onSaved={(saved) => {
          setNotice(`Customer ${saved.code} saved.`)
          setVersion((current) => current + 1)
        }} />
      </Panel>
      <Panel title={customer.active ? 'Deactivate' : 'Reactivate'}>
        <ActiveToggle what="customer" active={customer.active} mutation={setActive} onDone={setNotice} />
      </Panel>
    </>
  )
}
