import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { items } from '../../test/fixtures'
import { fakeBackend, meRoute, renderApp } from '../../test/renderApp'
import { dashboardSample } from '../../test/reports/samples'

describe('Owner dashboard (R15.7)', () => {
  it('shows the variance alerts with run number and percentage, flagged in danger', async () => {
    fakeBackend({ ...meRoute('OWNER'), 'GET /api/dashboard': dashboardSample, 'GET /api/items?status=all': items })
    renderApp('/dashboard', 'OWNER')

    const alerts = await screen.findByRole('table', { name: 'Variance alerts' })
    const oil = await within(alerts).findByRole('row', { name: /Cooking oil/ })
    expect(oil).toHaveTextContent('RUN/2026-27/0001')
    expect(within(oil).getByText('+5.00% Flagged')).toHaveClass('text-danger', 'font-semibold')
    const yieldRow = within(alerts).getByRole('row', { name: /Yield \(good output\)/ })
    expect(within(yieldRow).getByText('−6.00% Flagged')).toHaveClass('text-danger')
    expect(within(alerts).getByRole('row', { name: /200 g pouch/ })).toHaveTextContent('+3.19% Flagged')
  })

  it('shows outstanding, cost per packet, today\'s orders and low stock as the server sends them', async () => {
    const lowFlour = {
      ...dashboardSample,
      stockLevels: dashboardSample.stockLevels.map((level) =>
        level.itemCode === 'FLOUR' ? { ...level, reorderLevel: 150, lowStock: true } : level),
    }
    fakeBackend({ ...meRoute('ADMIN'), 'GET /api/dashboard': lowFlour, 'GET /api/items?status=all': items })
    renderApp('/dashboard', 'ADMIN')

    expect(await screen.findByText('₹3,875.00')).toBeInTheDocument()
    expect(within(screen.getByRole('table', { name: 'Top customers by outstanding' })).getByRole('row', { name: /Retailer A/ }))
      .toHaveTextContent('3,875.00')
    expect(within(screen.getByRole('table', { name: 'Cost per packet' })).getByRole('row', { name: /Murukku 200 g/ }))
      .toHaveTextContent('31.14')
    const orders = screen.getByRole('table', { name: "Today's orders" })
    expect(within(orders).getByRole('row', { name: 'Submitted 1' })).toBeInTheDocument()
    expect(within(orders).getByRole('row', { name: 'Dispatched 1' })).toBeInTheDocument()
    const stock = screen.getByRole('table', { name: 'Stock levels' })
    expect(within(within(stock).getByRole('row', { name: /^Flour/ })).getByText('Low stock')).toHaveClass('text-danger')
    expect(within(stock).getAllByText('Low stock')).toHaveLength(1)
  })

  it('is the owner\'s home, and not open to the auditor', async () => {
    fakeBackend({ ...meRoute('AUDITOR') })
    renderApp('/dashboard', 'AUDITOR')

    expect(await screen.findByRole('heading', { name: 'Not allowed' })).toBeInTheDocument()
  })
})
