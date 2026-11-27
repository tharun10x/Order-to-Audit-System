import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'

import type { ActualsRequest } from '../../api/types'
import { draftRun, items, postedRun } from '../../test/fixtures'
import { fakeBackend, jsonResponse, meRoute, renderApp } from '../../test/renderApp'

const row = (table: HTMLElement, name: string) => within(table).getByRole('row', { name: new RegExp(`^${name}`) })

describe('Posted run (R8.7 to R8.9, R13)', () => {
  it('shows flagged variances in danger with the percentage and the word', async () => {
    fakeBackend({ ...meRoute('ADMIN'), 'GET /api/production-runs/5': postedRun(), 'GET /api/items': items })
    renderApp('/production/runs/5', 'ADMIN')

    const table = await screen.findByRole('table', { name: 'Planned vs actual' })
    const oil = within(row(table, 'Cooking oil')).getByText('+5.00% Flagged')
    expect(oil).toHaveClass('text-danger', 'font-semibold')
    expect(within(row(table, '200 g pouch')).getByText('+3.19% Flagged')).toHaveClass('text-danger')
    expect(within(row(table, 'Good output')).getByText('−6.00% Flagged')).toHaveClass('text-danger')
    // Within tolerance: the percentage, not flagged
    const flour = within(row(table, 'Flour')).getByText('0.00%')
    expect(flour).not.toHaveClass('text-danger')
    expect(within(row(table, 'Flour')).queryByText(/Flagged/)).not.toBeInTheDocument()
  })

  it('shows the cost block from the backend and no actions for a read-only role', async () => {
    fakeBackend({ ...meRoute('AUDITOR'), 'GET /api/production-runs/5': postedRun(), 'GET /api/items': items })
    renderApp('/production/runs/5', 'AUDITOR')

    expect(await screen.findByText('₹5,855.00')).toBeInTheDocument()
    expect(screen.getByText('₹31.14')).toBeInTheDocument()
    expect(screen.getByText('37.71%')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Add overhead' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Post run' })).not.toBeInTheDocument()
  })
})

describe('Draft run (R8.2, R8.4)', () => {
  it('sends the unit chosen for each line', async () => {
    const { calls } = fakeBackend({
      ...meRoute('PRODUCTION'),
      'GET /api/production-runs/5': draftRun(),
      'GET /api/items': items,
      'PUT /api/production-runs/5/actuals': () => jsonResponse(draftRun()),
    })
    renderApp('/production/runs/5', 'PRODUCTION')

    const oil = await screen.findByLabelText('Actual Cooking oil')
    // The unit choices come from /api/items, so the selector appears once they are loaded
    await userEvent.selectOptions(await screen.findByLabelText('Unit for Cooking oil'), 'ml')
    await userEvent.clear(oil)
    await userEvent.type(oil, '10500')
    // Before posting, packaging has no variance yet: its standard follows the good output (R8.6)
    const planned = screen.getByRole('table', { name: 'Planned vs actual' })
    expect(within(row(planned, 'Carton')).getByText('Set when posted')).toBeInTheDocument()
    expect(within(row(planned, 'Carton')).queryByText(/Flagged/)).not.toBeInTheDocument()
    expect(within(row(planned, 'Cooking oil')).getByText('+5.00% Flagged')).toBeInTheDocument()
    // A pouch can only be counted in pieces, so there is no selector to get wrong
    expect(screen.queryByLabelText('Unit for 200 g pouch')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Save actuals' }))

    await screen.findByText('Actuals saved. The run is still a draft.')
    const sent = calls.find((call) => call.method === 'PUT')!.body as ActualsRequest
    expect(sent.lines.find((line) => line.itemCode === 'OIL')).toEqual({ itemCode: 'OIL', actualQty: 10500, scrapQty: 0, uomCode: 'ml' })
    expect(sent.lines.find((line) => line.itemCode === 'POUCH')).toEqual({ itemCode: 'POUCH', actualQty: 194, scrapQty: 6, uomCode: 'pc' })
    expect(sent.actualOutput).toBe(188)
  })

  it('lists what leaves and enters stock before posting, and shows each shortage line on a 409', async () => {
    const { calls } = fakeBackend({
      ...meRoute('PRODUCTION'),
      'GET /api/production-runs/5': draftRun(),
      'GET /api/items': items,
      'POST /api/production-runs/5/post': () => jsonResponse({
        status: 409, error: 'Conflict',
        message: 'Insufficient stock for 2 items: FLOUR: available 30.0000, requested 40.0000, short by 10.0000; OIL: available 5.0000, requested 10.5000, short by 5.5000',
        details: [
          'FLOUR: available 30.0000, requested 40.0000, short by 10.0000',
          'OIL: available 5.0000, requested 10.5000, short by 5.5000',
        ],
      }, 409),
    })
    renderApp('/production/runs/5', 'PRODUCTION')

    await userEvent.click(await screen.findByRole('button', { name: 'Post run' }))
    const leaves = screen.getByRole('list', { name: 'Leaves stock' })
    expect(within(leaves).getAllByRole('listitem').map((item) => item.textContent)).toEqual([
      'Flour: 40 kg', 'Cooking oil: 10.5 L', 'Salt and spice mix: 1.2 kg', '200 g pouch: 194 pc', 'Carton (20 packets): 9 pc',
    ])
    expect(within(screen.getByRole('list', { name: 'Enters stock' })).getByRole('listitem')).toHaveTextContent('Murukku 200 g: 188 pkt')
    expect(calls.some((call) => call.method === 'POST')).toBe(false)   // nothing sent until confirmed

    await userEvent.click(screen.getByRole('button', { name: 'Yes, post RUN/2026-27/0001' }))

    const alert = await screen.findByRole('alert')
    expect(within(alert).getAllByRole('listitem').map((item) => item.textContent)).toEqual([
      'FLOUR: available 30.0000, requested 40.0000, short by 10.0000',
      'OIL: available 5.0000, requested 10.5000, short by 5.5000',
    ])
  })
})
