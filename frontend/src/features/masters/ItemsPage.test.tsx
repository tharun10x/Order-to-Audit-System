import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'

import type { Item, ItemRequest } from '../../api/types'
import { items, material } from '../../test/fixtures'
import { fakeBackend, jsonResponse, meRoute, renderApp } from '../../test/renderApp'

const murukku500: Item = {
  code: 'MURUKKU500', name: 'Murukku 500 g', kind: 'FG', baseUnit: 'pkt', units: ['pkt', 'ctn'], hasActiveBom: false,
  reorderLevel: null, hsnCode: '21069099', gstRate: 5, sellingPrice: 110, packWeightKg: 0.5, packetsPerCarton: 10,
  active: true, baseUnitLockReason: null,
}

const lists = {
  'GET /api/items?status=active': items,
  'GET /api/items?status=all': items,
}

describe('Item form (R5.1, M7, M9)', () => {
  it('shows each missing finished-good field under its input and sends nothing', async () => {
    const { calls } = fakeBackend({ ...meRoute('ADMIN'), ...lists })
    renderApp('/masters/items/new', 'ADMIN')

    await userEvent.selectOptions(await screen.findByLabelText('Kind'), 'Finished good')
    // A finished good is counted in packets: no other base unit is offered
    const unit = screen.getByLabelText('Base unit') as HTMLSelectElement
    expect([...unit.options].map((option) => option.value)).toEqual(['pkt'])
    await userEvent.type(screen.getByLabelText('HSN code'), '2106')
    await userEvent.type(screen.getByLabelText('Selling price per packet (₹)'), '0')
    await userEvent.type(screen.getByLabelText('Packets per carton'), '2.5')
    await userEvent.click(screen.getByRole('button', { name: 'Create item' }))

    expect(screen.getByText('Enter the item code, for example MURUKKU500.')).toBeInTheDocument()
    expect(screen.getByText('Enter the name as it should appear on documents.')).toBeInTheDocument()
    expect(screen.getByText('Enter the GST rate in percent, from 0 to 100, for example 5.')).toBeInTheDocument()
    expect(screen.getByText('Enter the price per packet before GST, above 0 with up to 2 decimals.')).toBeInTheDocument()
    expect(screen.getByText('Enter the weight of one packet in kg, for example 0.5 for 500 g.')).toBeInTheDocument()
    expect(screen.getByText('Enter a whole number of packets from 1 to 10,000, or leave it empty.')).toBeInTheDocument()
    expect(screen.queryByText(/Enter the HSN code/)).not.toBeInTheDocument()   // 2106 is a valid 4-digit HSN
    // The message is tied to its field, so a screen reader reads it with the input
    expect(screen.getByLabelText('Name')).toHaveAccessibleDescription('Enter the name as it should appear on documents.')
    expect(screen.getByRole('alert')).toHaveTextContent('Some values need correcting. Each one is marked above.')
    expect(calls.some((call) => call.method === 'POST')).toBe(false)
  })

  it('creates a finished good with the values as typed and opens it', async () => {
    // The backend lists the new item once it is created
    let all = items
    const { calls } = fakeBackend({
      ...meRoute('ADMIN'),
      ...lists,
      'GET /api/items?status=all': () => jsonResponse(all),
      'POST /api/items': () => {
        all = [...items, murukku500]
        return jsonResponse(murukku500, 201)
      },
    })
    renderApp('/masters/items/new', 'ADMIN')

    await userEvent.type(await screen.findByLabelText('Code'), 'murukku500')
    expect(screen.getByLabelText('Code')).toHaveValue('MURUKKU500')
    await userEvent.type(screen.getByLabelText('Name'), 'Murukku 500 g')
    await userEvent.selectOptions(screen.getByLabelText('Kind'), 'Finished good')
    await userEvent.type(screen.getByLabelText('HSN code'), '21069099')
    await userEvent.type(screen.getByLabelText('GST rate (%)'), '5')
    await userEvent.type(screen.getByLabelText('Selling price per packet (₹)'), '110')
    await userEvent.type(screen.getByLabelText('Pack weight (kg)'), '0.5')
    await userEvent.type(screen.getByLabelText('Packets per carton'), '10')
    await userEvent.click(screen.getByRole('button', { name: 'Create item' }))

    expect(await screen.findByText('Item MURUKKU500 created.')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Edit Murukku 500 g' })).toBeInTheDocument()
    const sent = calls.find((call) => call.method === 'POST')!.body as ItemRequest
    expect(sent).toEqual({
      code: 'MURUKKU500', name: 'Murukku 500 g', kind: 'FG', baseUnit: 'pkt', reorderLevel: null,
      hsnCode: '21069099', gstRate: '5', sellingPrice: '110', packWeightKg: '0.5', packetsPerCarton: 10,
    })
  })

  it('shows the base unit as locked, with the reason, once the item is in use', async () => {
    fakeBackend({ ...meRoute('ADMIN'), ...lists })
    renderApp('/masters/items/FLOUR', 'ADMIN')

    expect(await screen.findByText('kg (kilogram), locked')).toBeInTheDocument()
    expect(screen.getByText('The base unit stays kg: FLOUR has stock movements, and the ledger quantities are in kg.')).toBeInTheDocument()
    expect(screen.queryByLabelText('Base unit')).not.toBeInTheDocument()
    // Code and kind never change (M3)
    expect(screen.queryByLabelText('Code')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Kind')).not.toBeInTheDocument()
  })

  it('shows the backend refusal when an item in an active recipe is deactivated', async () => {
    fakeBackend({
      ...meRoute('ADMIN'),
      ...lists,
      'POST /api/items/FLOUR/deactivate': () => jsonResponse({
        status: 409, error: 'Conflict', details: [],
        message: 'FLOUR is in the active recipe of MURUKKU200. Activate a recipe version without it first',
      }, 409),
    })
    renderApp('/masters/items/FLOUR', 'ADMIN')

    await userEvent.click(await screen.findByRole('button', { name: 'Deactivate item' }))
    expect(await screen.findByText('FLOUR is in the active recipe of MURUKKU200. Activate a recipe version without it first.'))
      .toBeInTheDocument()
  })

  it('filters by status through ?status=', async () => {
    const inactive = material('OLDBAG', 'Old bag', 'PACKAGING', 'pc', ['pc'], { active: false })
    const { calls } = fakeBackend({ ...meRoute('ADMIN'), ...lists, 'GET /api/items?status=inactive': [inactive] })
    const router = renderApp('/masters/items', 'ADMIN')

    await userEvent.selectOptions(await screen.findByLabelText('Show'), 'Inactive')
    const table = await screen.findByRole('table', { name: 'Items' })
    expect(within(table).getByRole('link', { name: 'OLDBAG' })).toBeInTheDocument()
    expect(within(table).getByText('Inactive')).toBeInTheDocument()
    expect(router.state.location.search).toBe('?status=inactive')
    expect(calls.some((call) => call.url === '/api/items?status=inactive')).toBe(true)
  })
})

describe('Items for a read-only role (R2)', () => {
  it('shows details, without a form or actions', async () => {
    fakeBackend({ ...meRoute('PRODUCTION'), ...lists })
    renderApp('/masters/items/MURUKKU200', 'PRODUCTION')

    expect(await screen.findByRole('heading', { name: 'Murukku 200 g' })).toBeInTheDocument()
    expect(screen.getByText('₹50.00 per packet')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'New item' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Save|Deactivate/ })).not.toBeInTheDocument()
    // Production can open items, recipes and suppliers, not customers
    const tabs = screen.getByRole('navigation', { name: 'Masters' })
    expect(within(tabs).getAllByRole('link').map((link) => link.textContent)).toEqual(['Items', 'Recipes', 'Suppliers'])
  })

  it('cannot open the new-item page', async () => {
    fakeBackend({ ...meRoute('AUDITOR'), ...lists })
    renderApp('/masters/items/new', 'AUDITOR')

    expect(await screen.findByRole('heading', { name: 'Not allowed' })).toBeInTheDocument()
  })
})
