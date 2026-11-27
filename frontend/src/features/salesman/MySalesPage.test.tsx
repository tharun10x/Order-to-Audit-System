import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'

import { fakeBackend, meRoute, renderApp } from '../../test/renderApp'
import { reportSamples } from '../../test/reports/samples'

const MONTH = 'from=2026-11-01&to=2026-11-30'

describe('My sales (brief 4.1, R2.2)', () => {
  it('shows the salesman\'s own customers from my-sales for the business month, with the server\'s totals', async () => {
    const { calls } = fakeBackend({
      ...meRoute('SALESMAN'),
      [`GET /api/reports/my-sales?${MONTH}&format=json`]: reportSamples['my-sales'],
    })
    renderApp('/sales/my-sales', 'SALESMAN')

    const list = await screen.findByRole('list', { name: 'My customers' })
    const customers = within(list).getAllByRole('listitem')
    expect(customers).toHaveLength(1)
    expect(customers[0]).toHaveTextContent('Retailer A')
    expect(customers[0]).toHaveTextContent('Invoiced₹7,875.00')
    expect(customers[0]).toHaveTextContent('Outstanding₹3,875.00')
    expect(screen.queryByText('Distributor B')).not.toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Total' })).toHaveTextContent('Orders1Invoiced₹7,875.00Outstanding₹3,875.00')

    expect(screen.getByLabelText('From')).toHaveValue('2026-11-01')
    expect(screen.getByLabelText('To')).toHaveValue('2026-11-30')
    // Only the salesman's own report: no customer, invoice or outstanding endpoints
    expect(calls.filter((call) => call.url !== '/api/auth/me').map((call) => call.url))
      .toEqual([`/api/reports/my-sales?${MONTH}&format=json`])
  })

  it('is in the salesman\'s navigation and asks again for another period', async () => {
    const { calls } = fakeBackend({
      ...meRoute('SALESMAN'),
      [`GET /api/reports/my-sales?${MONTH}&format=json`]: reportSamples['my-sales'],
      'GET /api/reports/my-sales?from=2026-11-15&to=2026-11-30&format=json': reportSamples['my-sales'],
    })
    renderApp('/sales/my-sales', 'SALESMAN')

    const nav = screen.getByRole('navigation', { name: 'Main' })
    expect(within(nav).getAllByRole('link').map((link) => link.textContent)).toEqual(['New order', 'My orders', 'My sales'])
    await screen.findByRole('list', { name: 'My customers' })

    const from = screen.getByLabelText('From')
    await userEvent.clear(from)
    await userEvent.type(from, '2026-11-15')
    await screen.findByRole('list', { name: 'My customers' })
    expect(calls.map((call) => call.url)).toContain('/api/reports/my-sales?from=2026-11-15&to=2026-11-30&format=json')
  })
})
