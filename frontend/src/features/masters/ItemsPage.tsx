import { useState, type ReactNode } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'

import { useMasterItems, useSetItemActive } from '../../api/masters'
import type { Item } from '../../api/types'
import { useSession } from '../../auth/authContext'
import { can } from '../../auth/permissions'
import { Facts, LinkButton } from '../../components/Layout'
import { EmptyState, ErrorBox, SuccessLine } from '../../components/Messages'
import { Panel } from '../../components/Panel'
import { Table, Td, Th } from '../../components/Table'
import { formatMoney, formatNumber, formatQty } from '../../lib/format'
import { BASE_UNIT, ITEM_KIND_ONE } from '../../lib/labels'
import { ActiveToggle } from './ActiveToggle'
import { Fixed } from './formParts'
import { ItemForm } from './ItemForm'
import { useMasterStatus, useStatusQuery } from './masterStatus'
import { ActiveWord, StatusFilter } from './StatusFilter'

/**
 * R5.1, R5.2: raw materials, packaging and finished goods. The list, then the selected item below: a form for the
 * admin, its details for everyone else (UI guide 7: table, then the selected record).
 */
export function ItemsPage({ creating = false }: { creating?: boolean }) {
  const session = useSession()
  const manage = can.manageMasters(session.user.role)
  const status = useMasterStatus()
  const query = useStatusQuery()
  const { code } = useParams()
  const navigate = useNavigate()
  const notice = (useLocation().state as { notice?: string } | null)?.notice ?? null
  const items = useMasterItems(status)
  // The selected item is looked up in every item, so changing the filter (or deactivating it) keeps it open
  const everyItem = useMasterItems('all')
  const selected = code ? everyItem.data?.find((item) => item.code === code) : undefined

  return (
    <>
      <Panel title="Items">
        <div className="mb-4 flex flex-wrap items-end justify-between gap-4">
          <StatusFilter />
          {manage ? <LinkButton variant="primary" to={`/masters/items/new${query}`}>New item</LinkButton> : null}
        </div>
        {items.error ? <ErrorBox error={items.error} /> : null}
        {items.data && items.data.length === 0 ? (
          <EmptyState>{status === 'inactive' ? 'No inactive items.' : 'No items yet. Raw materials, packaging and products created here appear in this list.'}</EmptyState>
        ) : null}
        {items.data && items.data.length > 0 ? (
          <Table label="Items">
            <thead>
              <tr>
                <Th>Code</Th>
                <Th>Name</Th>
                <Th>Kind</Th>
                <Th>Base unit</Th>
                <Th numeric>Price (₹)</Th>
                <Th numeric>GST</Th>
                <Th numeric>Packets per carton</Th>
                <Th>Status</Th>
              </tr>
            </thead>
            <tbody>
              {items.data.map((item) => (
                <tr key={item.code} className={item.code === code ? 'bg-primary-tint' : undefined}>
                  <Td className="whitespace-nowrap">
                    <Link to={`/masters/items/${encodeURIComponent(item.code)}${query}`} className="font-medium text-primary hover:underline"
                      aria-current={item.code === code ? 'true' : undefined}>
                      {item.code}
                    </Link>
                  </Td>
                  <Td>{item.name}</Td>
                  <Td>{ITEM_KIND_ONE[item.kind]}</Td>
                  <Td>{item.baseUnit}</Td>
                  <Td numeric>{item.sellingPrice === null ? '' : formatMoney(item.sellingPrice)}</Td>
                  <Td numeric>{item.gstRate === null ? '' : `${formatNumber(item.gstRate)}%`}</Td>
                  <Td numeric>{item.packetsPerCarton === null ? '' : formatNumber(item.packetsPerCarton)}</Td>
                  <Td><ActiveWord active={item.active} /></Td>
                </tr>
              ))}
            </tbody>
          </Table>
        ) : null}
      </Panel>

      {creating && manage ? (
        <Panel title="New item">
          <ItemForm
            item={null}
            onSaved={(item) => navigate(`/masters/items/${encodeURIComponent(item.code)}${query}`, {
              state: { notice: `Item ${item.code} created.` },
            })}
          />
        </Panel>
      ) : null}
      {code && everyItem.data && !selected ? <ErrorBox message={`There is no item with the code ${code}.`} /> : null}
      {selected ? <ItemDetail key={selected.code} item={selected} manage={manage} initialNotice={notice} /> : null}
    </>
  )
}

function ItemDetail({ item, manage, initialNotice }: { item: Item; manage: boolean; initialNotice: string | null }) {
  const [notice, setNotice] = useState(initialNotice)
  const setActive = useSetItemActive(item.code)
  // A new key after each save starts the form again from the saved values
  const [version, setVersion] = useState(0)
  const message = notice ? <div className="mb-4"><SuccessLine>{notice}</SuccessLine></div> : null

  if (!manage) {
    return (
      <Panel title={item.name}>
        <ItemFacts item={item} />
      </Panel>
    )
  }
  return (
    <>
      <Panel title={`Edit ${item.name}`}>
        {message}
        <ItemForm key={version} item={item} onSaved={(saved) => {
          setNotice(`Item ${saved.code} saved.`)
          setVersion((current) => current + 1)
        }} />
      </Panel>
      <Panel title={item.active ? 'Deactivate' : 'Reactivate'}>
        <ActiveToggle what="item" active={item.active} mutation={setActive} onDone={setNotice} />
      </Panel>
    </>
  )
}

/** Read-only roles (production, auditor, owner) see the item's details. */
function ItemFacts({ item }: { item: Item }) {
  const facts: [string, ReactNode][] = [
    ['Code', item.code],
    ['Kind', ITEM_KIND_ONE[item.kind]],
    ['Base unit', BASE_UNIT[item.baseUnit] ?? item.baseUnit],
    ['Reorder level', item.reorderLevel === null ? 'None' : formatQty(item.reorderLevel, item.baseUnit)],
    ['Status', <ActiveWord key="status" active={item.active} />],
  ]
  if (item.kind === 'FG') {
    facts.push(
      ['HSN code', item.hsnCode ?? ''],
      ['GST rate', item.gstRate === null ? '' : `${formatNumber(item.gstRate)}%`],
      ['Selling price', item.sellingPrice === null ? '' : `${formatMoney(item.sellingPrice, { withSymbol: true })} per packet`],
      ['Pack weight', item.packWeightKg === null ? '' : formatQty(item.packWeightKg, 'kg')],
      ['Packets per carton', item.packetsPerCarton === null ? 'Not sold in cartons' : formatNumber(item.packetsPerCarton)],
      ['Recipe', item.hasActiveBom ? 'Has an active recipe' : 'No active recipe'],
    )
  }
  return (
    <div className="flex flex-col gap-4">
      <Facts items={facts} />
      {item.baseUnitLockReason ? <Fixed label="Base unit lock" value={`${item.baseUnitLockReason}.`} /> : null}
    </div>
  )
}
