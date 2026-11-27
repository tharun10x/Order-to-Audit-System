import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'

import type { PurchaseRequest } from '../../api/types'
import { items } from '../../test/fixtures'
import { fakeBackend, jsonResponse, meRoute, renderApp } from '../../test/renderApp'

describe('Record purchase (R7.1, R3.3)', () => {
  it('offers only the units the item accepts and sends the chosen uomCode', async () => {
    const { calls } = fakeBackend({
      ...meRoute('PRODUCTION'),
      'GET /api/suppliers': [{ id: 1, name: 'Sample Flour Mills', gstin: null, stateCode: '33' }],
      'GET /api/items': items,
      'POST /api/purchases': () => jsonResponse({
        id: 1, supplierId: 1, supplierName: 'Sample Flour Mills', purchaseDate: '2026-11-28', billNo: 'B-1',
        status: 'POSTED', total: 2700, lines: [{ itemCode: 'FLOUR', itemName: 'Flour', qty: 60, unit: 'kg', rate: 45, amount: 2700 }],
      }, 201),
    })
    renderApp('/purchases/new', 'PRODUCTION')

    // Finished goods cannot be bought
    const item = await screen.findByLabelText('Item on line 1')
    expect(screen.queryByRole('option', { name: 'Murukku 200 g' })).not.toBeInTheDocument()

    await userEvent.selectOptions(screen.getByLabelText('Supplier'), 'Sample Flour Mills')
    await userEvent.type(screen.getByLabelText('Bill no'), 'B-1')
    await userEvent.selectOptions(item, 'FLOUR')
    const unit = screen.getByLabelText('Unit on line 1')
    expect([...(unit as HTMLSelectElement).options].map((option) => option.value)).toEqual(['kg', 'g'])
    await userEvent.selectOptions(unit, 'g')
    await userEvent.type(screen.getByLabelText('Quantity on line 1'), '60000')
    await userEvent.type(screen.getByLabelText('Rate on line 1'), '0.045')
    expect(screen.getByText('2,700.00 for 60,000 g')).toBeInTheDocument()   // the estimate

    await userEvent.click(screen.getByRole('button', { name: 'Record purchase' }))

    // The saved amount is the server's
    expect(await screen.findByText('Bill B-1 recorded: ₹2,700.00. Stock is updated.')).toBeInTheDocument()
    const sent = calls.find((call) => call.method === 'POST')!.body as PurchaseRequest
    expect(sent).toEqual({
      supplierId: 1, purchaseDate: '2026-11-28', billNo: 'B-1',
      lines: [{ itemCode: 'FLOUR', qty: 60000, uomCode: 'g', rate: 0.045 }],
    })
  })

  it('allows one line per item', async () => {
    fakeBackend({
      ...meRoute('ADMIN'),
      'GET /api/suppliers': [{ id: 1, name: 'Sample Flour Mills', gstin: null, stateCode: '33' }],
      'GET /api/items': items,
    })
    renderApp('/purchases/new', 'ADMIN')

    await userEvent.selectOptions(await screen.findByLabelText('Item on line 1'), 'FLOUR')
    await userEvent.click(screen.getByRole('button', { name: 'Add line' }))

    const second = screen.getByLabelText('Item on line 2') as HTMLSelectElement
    expect([...second.options].find((option) => option.value === 'FLOUR')!.disabled).toBe(true)
  })
})
