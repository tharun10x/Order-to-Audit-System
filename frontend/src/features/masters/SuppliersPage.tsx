import { useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'

import { useMasterSuppliers, useSetSupplierActive } from '../../api/masters'
import type { Supplier } from '../../api/types'
import { useSession } from '../../auth/authContext'
import { can } from '../../auth/permissions'
import { Facts, LinkButton } from '../../components/Layout'
import { EmptyState, ErrorBox, SuccessLine } from '../../components/Messages'
import { Panel } from '../../components/Panel'
import { Table, Td, Th } from '../../components/Table'
import { describeState } from '../../lib/gstStates'
import { ActiveToggle } from './ActiveToggle'
import { SupplierForm } from './SupplierForm'
import { useMasterStatus, useStatusQuery } from './masterStatus'
import { ActiveWord, StatusFilter } from './StatusFilter'

/** R5.3: suppliers, then the selected one below: a form for the admin, its details for production. */
export function SuppliersPage({ creating = false }: { creating?: boolean }) {
  const session = useSession()
  const manage = can.manageMasters(session.user.role)
  const status = useMasterStatus()
  const query = useStatusQuery()
  const { supplierId } = useParams()
  const navigate = useNavigate()
  const notice = (useLocation().state as { notice?: string } | null)?.notice ?? null
  const suppliers = useMasterSuppliers(status)
  const everySupplier = useMasterSuppliers('all')
  const id = supplierId ? Number(supplierId) : null
  const selected = id ? everySupplier.data?.find((supplier) => supplier.id === id) : undefined

  return (
    <>
      <Panel title="Suppliers">
        <div className="mb-4 flex flex-wrap items-end justify-between gap-4">
          <StatusFilter />
          {manage ? <LinkButton variant="primary" to={`/masters/suppliers/new${query}`}>New supplier</LinkButton> : null}
        </div>
        {suppliers.error ? <ErrorBox error={suppliers.error} /> : null}
        {suppliers.data && suppliers.data.length === 0 ? (
          <EmptyState>{status === 'inactive' ? 'No inactive suppliers.' : 'No suppliers yet. Suppliers created here can be picked on a purchase.'}</EmptyState>
        ) : null}
        {suppliers.data && suppliers.data.length > 0 ? (
          <Table label="Suppliers">
            <thead>
              <tr>
                <Th>Name</Th>
                <Th>State</Th>
                <Th>GSTIN</Th>
                <Th>Phone</Th>
                <Th>Status</Th>
              </tr>
            </thead>
            <tbody>
              {suppliers.data.map((supplier) => (
                <tr key={supplier.id} className={supplier.id === id ? 'bg-primary-tint' : undefined}>
                  <Td>
                    <Link to={`/masters/suppliers/${supplier.id}${query}`} className="font-medium text-primary hover:underline"
                      aria-current={supplier.id === id ? 'true' : undefined}>
                      {supplier.name}
                    </Link>
                  </Td>
                  <Td>{describeState(supplier.stateCode)}</Td>
                  <Td className="num">{supplier.gstin ?? ''}</Td>
                  <Td className="num">{supplier.phone ?? ''}</Td>
                  <Td><ActiveWord active={supplier.active} /></Td>
                </tr>
              ))}
            </tbody>
          </Table>
        ) : null}
      </Panel>

      {creating && manage ? (
        <Panel title="New supplier">
          <SupplierForm
            supplier={null}
            onSaved={(supplier) => navigate(`/masters/suppliers/${supplier.id}${query}`, {
              state: { notice: `Supplier ${supplier.name} created.` },
            })}
          />
        </Panel>
      ) : null}
      {id && everySupplier.data && !selected ? <ErrorBox message="This supplier does not exist." /> : null}
      {selected ? <SupplierDetail key={selected.id} supplier={selected} manage={manage} initialNotice={notice} /> : null}
    </>
  )
}

function SupplierDetail({ supplier, manage, initialNotice }: { supplier: Supplier; manage: boolean; initialNotice: string | null }) {
  const [notice, setNotice] = useState(initialNotice)
  // A new key after each save starts the form again from the saved values
  const [version, setVersion] = useState(0)
  const setActive = useSetSupplierActive(supplier.id)

  if (!manage) {
    return (
      <Panel title={supplier.name}>
        <Facts items={[
          ['State', describeState(supplier.stateCode) || 'Not given'],
          ['GSTIN', supplier.gstin ?? 'Not registered'],
          ['Phone', supplier.phone ?? ''],
          ['Status', <ActiveWord key="status" active={supplier.active} />],
        ]} />
      </Panel>
    )
  }
  return (
    <>
      <Panel title={`Edit ${supplier.name}`}>
        {notice ? <div className="mb-4"><SuccessLine>{notice}</SuccessLine></div> : null}
        <SupplierForm key={version} supplier={supplier} onSaved={(saved) => {
          setNotice(`Supplier ${saved.name} saved.`)
          setVersion((current) => current + 1)
        }} />
      </Panel>
      <Panel title={supplier.active ? 'Deactivate' : 'Reactivate'}>
        <ActiveToggle what="supplier" active={supplier.active} mutation={setActive} onDone={setNotice} />
      </Panel>
    </>
  )
}
