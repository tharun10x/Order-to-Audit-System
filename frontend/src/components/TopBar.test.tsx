import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import type { Role } from '../api/types'
import { fakeBackend, meRoute, renderApp } from '../test/renderApp'

/** R2: the top bar shows only the pages a role can open. */
describe('navigation per role', () => {
  it.each([
    ['ADMIN', '/orders', ['Orders', 'Invoices', 'Payments', 'Purchases', 'Production', 'Stock', 'Masters', 'Reports', 'Dashboard']],
    ['PRODUCTION', '/stock', ['Production runs', 'Purchases', 'Stock', 'Masters', 'Reports']],
    ['AUDITOR', '/reports', ['Reports', 'Orders', 'Invoices', 'Payments', 'Purchases', 'Production', 'Stock', 'Masters']],
    ['OWNER', '/orders', ['Dashboard', 'Reports', 'Orders', 'Invoices', 'Payments', 'Purchases', 'Production', 'Stock', 'Masters']],
  ] as [Role, string, string[]][])('%s sees its own pages', async (role, path, pages) => {
    fakeBackend({ ...meRoute(role), 'GET /api/orders': [], 'GET /api/inventory/balances': [] })
    renderApp(path, role)

    const nav = await screen.findByRole('navigation', { name: 'Main' })
    expect(within(nav).getAllByRole('link').map((link) => link.textContent)).toEqual(pages)
  })

  it('production cannot open the invoice pages', async () => {
    fakeBackend({ ...meRoute('PRODUCTION') })
    renderApp('/invoices', 'PRODUCTION')

    expect(await screen.findByRole('heading', { name: 'Not allowed' })).toBeInTheDocument()
  })

  it('an auditor cannot open the record payment form', async () => {
    fakeBackend({ ...meRoute('AUDITOR') })
    renderApp('/payments/new', 'AUDITOR')

    expect(await screen.findByRole('heading', { name: 'Not allowed' })).toBeInTheDocument()
  })
})
