import { useState } from 'react'
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router'

import { useActivateRecipe, useMasterItems, useRecipes } from '../../api/masters'
import type { Bom, Item } from '../../api/types'
import { useSession } from '../../auth/authContext'
import { can } from '../../auth/permissions'
import { Button } from '../../components/Button'
import { Field } from '../../components/Field'
import { LinkButton, Select } from '../../components/Layout'
import { EmptyState, ErrorBox, SuccessLine } from '../../components/Messages'
import { Panel } from '../../components/Panel'
import { Table, Td, Th } from '../../components/Table'
import { istDate } from '../../lib/dates'
import { formatDate, formatNumber, formatQty } from '../../lib/format'
import { NewRecipeVersion } from './NewRecipeVersion'

/**
 * R5.6 to R5.10: a product's recipe versions, newest first, with the active one marked. A version is never
 * edited: the admin saves a new one (inactive) and activates it. Runs already created keep the quantities they
 * were created with (R8.5), so activating changes only new runs.
 */
export function RecipesPage({ creating = false }: { creating?: boolean }) {
  const session = useSession()
  const manage = can.manageMasters(session.user.role)
  const { productCode = '' } = useParams()
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const notice = (useLocation().state as { notice?: string } | null)?.notice ?? null
  const items = useMasterItems('all')
  const recipes = useRecipes(productCode)

  const products = (items.data ?? []).filter((item) => item.kind === 'FG')
  const product = products.find((item) => item.code === productCode)
  const versions = recipes.data ?? []
  // ?version= picks a version; otherwise the active one, or the newest
  const versionParam = Number(params.get('version'))
  const selected = versions.find((bom) => bom.version === versionParam) ?? versions.find((bom) => bom.active) ?? versions[0]

  if (items.error) {
    return <ErrorBox error={items.error} />
  }
  return (
    <>
      <Panel title="Recipes">
        <div className="mb-4 flex flex-wrap items-end justify-between gap-4">
          <div className="w-72">
            <Field label="Product">
              {({ id }) => (
                <Select id={id} value={productCode} disabled={!items.data}
                  onChange={(event) => navigate(event.target.value ? `/masters/recipes/${encodeURIComponent(event.target.value)}` : '/masters/recipes')}>
                  <option value="">{items.data ? 'Pick a product' : 'Loading products…'}</option>
                  {products.map((option) => (
                    <option key={option.code} value={option.code}>
                      {`${option.name}${option.active ? '' : ' (inactive)'}${option.hasActiveBom ? '' : ', no active recipe'}`}
                    </option>
                  ))}
                </Select>
              )}
            </Field>
          </div>
          {manage && product && !creating ? (
            product.active
              ? <LinkButton variant="primary" to={`/masters/recipes/${encodeURIComponent(product.code)}/new`}>New version</LinkButton>
              : <p className="text-sm text-muted">{`${product.name} is inactive: reactivate it before giving it a recipe.`}</p>
          ) : null}
        </div>
        {notice ? <div className="mb-4"><SuccessLine>{notice}</SuccessLine></div> : null}
        {!productCode ? <EmptyState>Pick a product to see its recipe versions.</EmptyState> : null}
        {productCode && items.data && !product ? <ErrorBox message={`${productCode} is not a finished good, so it has no recipe.`} /> : null}
        {recipes.error ? <ErrorBox error={recipes.error} /> : null}
        {product && recipes.data && versions.length === 0 ? (
          <EmptyState>{`${product.name} has no recipe yet.${manage && !creating ? ' Create its first version with New version.' : ''}`}</EmptyState>
        ) : null}
        {product && versions.length > 0 ? (
          <Table label="Recipe versions">
            <thead>
              <tr>
                <Th>Version</Th>
                <Th numeric>Packets per batch</Th>
                <Th numeric>Lines</Th>
                <Th>Created</Th>
                <Th>Status</Th>
              </tr>
            </thead>
            <tbody>
              {versions.map((bom) => (
                <tr key={bom.id} className={bom.id === selected?.id ? 'bg-primary-tint' : undefined}>
                  <Td>
                    <Link to={`/masters/recipes/${encodeURIComponent(product.code)}?version=${bom.version}`}
                      className="font-medium text-primary hover:underline" aria-current={bom.id === selected?.id ? 'true' : undefined}>
                      {`Version ${bom.version}`}
                    </Link>
                  </Td>
                  <Td numeric>{formatNumber(bom.outputQty)}</Td>
                  <Td numeric>{bom.lines.length}</Td>
                  <Td className="whitespace-nowrap">{formatDate(istDate(bom.createdAt))}</Td>
                  <Td>{bom.active ? <span className="font-semibold text-primary">Active</span> : <span className="text-muted">Inactive</span>}</Td>
                </tr>
              ))}
            </tbody>
          </Table>
        ) : null}
      </Panel>

      {creating && manage && product && recipes.data ? (
        <Panel title={`New version of ${product.name}`}>
          <NewRecipeVersion
            product={product}
            latest={versions[0]}
            items={items.data ?? []}
            onCancel={() => navigate(`/masters/recipes/${encodeURIComponent(product.code)}`)}
            onSaved={(bom) => navigate(`/masters/recipes/${encodeURIComponent(product.code)}?version=${bom.version}`, {
              state: { notice: `Version ${bom.version} saved. It is inactive until you activate it.` },
            })}
          />
        </Panel>
      ) : null}
      {!creating && product && selected ? (
        <VersionPanel key={selected.id} product={product} bom={selected} active={versions.find((bom) => bom.active)} manage={manage} />
      ) : null}
    </>
  )
}

/** One version's lines, split into ingredients and packaging, and (admin) its activation. */
function VersionPanel({ product, bom, active, manage }: { product: Item; bom: Bom; active: Bom | undefined; manage: boolean }) {
  const activate = useActivateRecipe()
  const [confirming, setConfirming] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)
  const groups = [
    { title: 'Ingredients', lines: bom.lines.filter((line) => line.lineKind === 'INGREDIENT') },
    { title: 'Packaging', lines: bom.lines.filter((line) => line.lineKind === 'PACKAGING') },
  ]

  return (
    <Panel title={`${product.name}, version ${bom.version}${bom.active ? ' (active)' : ''}`}>
      {notice ? <div className="mb-4"><SuccessLine>{notice}</SuccessLine></div> : null}
      <p className="mb-4 text-sm">{`One batch gives ${formatQty(bom.outputQty, 'pkt')}.`}</p>
      <Table label={`Version ${bom.version} lines`}>
        <thead>
          <tr>
            <Th>Item</Th>
            <Th numeric>Quantity per batch</Th>
          </tr>
        </thead>
        {groups.filter((group) => group.lines.length > 0).map((group) => (
          <tbody key={group.title}>
            <tr>
              <Th scope="colgroup" colSpan={2} className="border-b border-rule pt-5 text-[13px] text-muted">{group.title}</Th>
            </tr>
            {group.lines.map((line) => (
              <tr key={line.itemCode}>
                <Td>{line.itemName}</Td>
                <Td numeric>{formatQty(line.qty, line.unit)}</Td>
              </tr>
            ))}
          </tbody>
        ))}
      </Table>

      {manage && !bom.active ? (
        <div className="mt-6 flex flex-col gap-3">
          {confirming ? (
            <div role="alertdialog" aria-labelledby="activate-what" className="rounded-control border border-border p-4 text-sm">
              <p id="activate-what" className="font-semibold">{`Activate version ${bom.version} of ${product.name}?`}</p>
              <p className="mt-2">
                {`New production runs will use version ${bom.version}${active ? ` instead of version ${active.version}` : ''}. `}
                Existing runs are not affected: they keep the quantities they were created with.
              </p>
              <div className="mt-4 flex flex-wrap gap-3">
                <Button variant="primary" loading={activate.isPending}
                  onClick={() => activate.mutate(bom.id, {
                    onSuccess: (saved) => setNotice(`Version ${saved.version} is now the active recipe of ${product.name}. New runs use it.`),
                    onSettled: () => setConfirming(false),
                  })}>
                  {`Yes, activate version ${bom.version}`}
                </Button>
                <Button onClick={() => setConfirming(false)} disabled={activate.isPending}>Not yet</Button>
              </div>
            </div>
          ) : (
            <div>
              <Button variant="primary" onClick={() => setConfirming(true)}>{`Activate version ${bom.version}`}</Button>
            </div>
          )}
          {activate.error ? <ErrorBox error={activate.error} /> : null}
        </div>
      ) : null}
    </Panel>
  )
}
