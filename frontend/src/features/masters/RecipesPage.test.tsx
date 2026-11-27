import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'

import type { Bom, BomRequest, Item } from '../../api/types'
import { items, material, murukkuItem } from '../../test/fixtures'
import { fakeBackend, jsonResponse, meRoute, renderApp } from '../../test/renderApp'

const murukku500: Item = {
  ...murukkuItem, code: 'MURUKKU500', name: 'Murukku 500 g', sellingPrice: 110, packWeightKg: 0.5, packetsPerCarton: 10,
  hasActiveBom: false, baseUnitLockReason: null,
}
const pouch500 = material('POUCH500', '500 g pouch', 'PACKAGING', 'pc', ['pc'])
const everyItem = [...items, murukku500, pouch500]

/** The benchmark recipe of the 200 g pack, version 1 and active. */
const recipe200: Bom = {
  id: 1, productCode: 'MURUKKU200', productName: 'Murukku 200 g', version: 1, outputQty: 200, active: true,
  createdAt: '2026-11-01T04:30:00Z',
  lines: [
    { itemCode: 'FLOUR', itemName: 'Flour', lineKind: 'INGREDIENT', qty: 40, unit: 'kg' },
    { itemCode: 'OIL', itemName: 'Cooking oil', lineKind: 'INGREDIENT', qty: 10, unit: 'L' },
    { itemCode: 'SPICE', itemName: 'Salt and spice mix', lineKind: 'INGREDIENT', qty: 1.2, unit: 'kg' },
    { itemCode: 'POUCH', itemName: '200 g pouch', lineKind: 'PACKAGING', qty: 200, unit: 'pc' },
    { itemCode: 'CARTON', itemName: 'Carton (20 packets)', lineKind: 'PACKAGING', qty: 10, unit: 'pc' },
  ],
}

const recipe500: Bom = {
  id: 2, productCode: 'MURUKKU500', productName: 'Murukku 500 g', version: 1, outputQty: 80, active: false,
  createdAt: '2026-11-28T05:00:00Z',
  lines: [
    ...recipe200.lines.filter((line) => line.lineKind === 'INGREDIENT'),
    { itemCode: 'POUCH500', itemName: '500 g pouch', lineKind: 'PACKAGING', qty: 80, unit: 'pc' },
    { itemCode: 'CARTON', itemName: 'Carton (20 packets)', lineKind: 'PACKAGING', qty: 8, unit: 'pc' },
  ],
}

const backend = {
  'GET /api/items?status=all': everyItem,
  'GET /api/boms?productCode=MURUKKU200': [recipe200],
}

describe('New recipe version: copy ingredients from another product (R5.10)', () => {
  it('shows the copied ingredients, keeps them out of the line picker and sends only the copy source and the other lines', async () => {
    const { calls } = fakeBackend({
      ...meRoute('ADMIN'),
      ...backend,
      'GET /api/boms?productCode=MURUKKU500': [],
      'POST /api/boms': () => jsonResponse(recipe500, 201),
    })
    renderApp('/masters/recipes/MURUKKU500/new', 'ADMIN')

    await userEvent.selectOptions(await screen.findByLabelText('Copy ingredients from'), 'Murukku 200 g')

    const copied = await screen.findByRole('table', { name: 'Copied ingredients' })
    expect(screen.getByRole('heading', { name: 'Ingredients copied from Murukku 200 g, version 1' })).toBeInTheDocument()
    expect(within(copied).getAllByRole('row').slice(1).map((row) => row.textContent))
      .toEqual(['Flour40 kg', 'Cooking oil10 L', 'Salt and spice mix1.2 kg'])
    // An item appears once per version: the copied ones cannot be picked again
    const item = screen.getByLabelText('Item on line 1') as HTMLSelectElement
    expect([...item.options].find((option) => option.value === 'FLOUR')!.disabled).toBe(true)
    expect([...item.options].find((option) => option.value === 'POUCH500')!.disabled).toBe(false)
    // Finished goods are never lines
    expect([...item.options].some((option) => option.value === 'MURUKKU200')).toBe(false)

    await userEvent.type(screen.getByLabelText('Packets per batch'), '80')
    await userEvent.selectOptions(item, '500 g pouch')
    await userEvent.type(screen.getByLabelText('Quantity on line 1'), '80')
    await userEvent.click(screen.getByRole('button', { name: 'Add line' }))
    await userEvent.selectOptions(screen.getByLabelText('Item on line 2'), 'Carton (20 packets)')
    await userEvent.type(screen.getByLabelText('Quantity on line 2'), '8')
    await userEvent.click(screen.getByRole('button', { name: 'Save as new version' }))

    expect(await screen.findByText('Version 1 saved. It is inactive until you activate it.')).toBeInTheDocument()
    const sent = calls.find((call) => call.method === 'POST')!.body as BomRequest
    expect(sent).toEqual({
      productCode: 'MURUKKU500', outputQty: '80', copyIngredientsFrom: 'MURUKKU200',
      lines: [{ itemCode: 'POUCH500', qty: '80', uomCode: 'pc' }, { itemCode: 'CARTON', qty: '8', uomCode: 'pc' }],
    })
  })

  it('takes lines the copy brings in out of the prefilled lines, and says so', async () => {
    fakeBackend({ ...meRoute('ADMIN'), ...backend, 'GET /api/boms?productCode=MURUKKU500': [recipe500] })
    renderApp('/masters/recipes/MURUKKU500/new', 'ADMIN')

    // The new version starts from the newest one: 3 ingredients and 2 packaging lines
    expect(await screen.findByLabelText('Item on line 5')).toHaveValue('CARTON')
    expect(screen.getByLabelText('Packets per batch')).toHaveValue('80')

    await userEvent.selectOptions(screen.getByLabelText('Copy ingredients from'), 'Murukku 200 g')

    expect(await screen.findByText('Taken out of the lines below, because the copy brings them in: Flour, Cooking oil, Salt and spice mix.'))
      .toBeInTheDocument()
    expect(screen.getByLabelText('Item on line 1')).toHaveValue('POUCH500')
    expect(screen.getByLabelText('Item on line 2')).toHaveValue('CARTON')
    expect(screen.queryByLabelText('Item on line 3')).not.toBeInTheDocument()
  })

  it('needs at least one raw material, copied or entered', async () => {
    const { calls } = fakeBackend({ ...meRoute('ADMIN'), ...backend, 'GET /api/boms?productCode=MURUKKU500': [] })
    renderApp('/masters/recipes/MURUKKU500/new', 'ADMIN')

    await userEvent.type(await screen.findByLabelText('Packets per batch'), '80.5')
    await userEvent.selectOptions(screen.getByLabelText('Item on line 1'), '500 g pouch')
    await userEvent.type(screen.getByLabelText('Quantity on line 1'), '0')
    await userEvent.click(screen.getByRole('button', { name: 'Save as new version' }))

    expect(screen.getByText('A recipe needs at least one raw material. Add one, or copy the ingredients of another product.')).toBeInTheDocument()
    expect(screen.getByText('Enter the packets one batch gives, a whole number above 0.')).toBeInTheDocument()
    expect(screen.getByText('Enter a quantity above 0 (up to 4 decimals).')).toBeInTheDocument()
    expect(calls.some((call) => call.method === 'POST')).toBe(false)
  })
})

describe('Recipe versions (R5.8)', () => {
  it('marks the active version and asks before activating another, saying existing runs are not affected', async () => {
    const version2: Bom = { ...recipe200, id: 3, version: 2, active: false, outputQty: 210, createdAt: '2026-11-28T05:00:00Z' }
    const { calls } = fakeBackend({
      ...meRoute('ADMIN'),
      ...backend,
      'GET /api/boms?productCode=MURUKKU200': [version2, recipe200],
      'POST /api/boms/3/activate': () => jsonResponse({ ...version2, active: true }),
    })
    renderApp('/masters/recipes/MURUKKU200?version=2', 'ADMIN')

    const history = await screen.findByRole('table', { name: 'Recipe versions' })
    expect(within(within(history).getByRole('row', { name: /^Version 1/ })).getByText('Active')).toHaveClass('text-primary')
    expect(within(within(history).getByRole('row', { name: /^Version 2/ })).getByText('Inactive')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Activate version 2' }))
    const dialog = screen.getByRole('alertdialog', { name: 'Activate version 2 of Murukku 200 g?' })
    expect(dialog).toHaveTextContent('New production runs will use version 2 instead of version 1.')
    expect(dialog).toHaveTextContent('Existing runs are not affected: they keep the quantities they were created with.')
    expect(calls.some((call) => call.method === 'POST')).toBe(false)   // nothing sent until confirmed

    await userEvent.click(within(dialog).getByRole('button', { name: 'Yes, activate version 2' }))
    expect(await screen.findByText('Version 2 is now the active recipe of Murukku 200 g. New runs use it.')).toBeInTheDocument()
    expect(calls.some((call) => call.method === 'POST' && call.url === '/api/boms/3/activate')).toBe(true)
  })

  it('is read-only for production', async () => {
    fakeBackend({ ...meRoute('PRODUCTION'), ...backend })
    renderApp('/masters/recipes/MURUKKU200', 'PRODUCTION')

    const lines = await screen.findByRole('table', { name: 'Version 1 lines' })
    expect(within(lines).getByRole('row', { name: 'Cooking oil 10 L' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'New version' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Activate/ })).not.toBeInTheDocument()
  })
})
