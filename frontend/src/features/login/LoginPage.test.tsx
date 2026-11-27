import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'

import type { Role } from '../../api/types'
import { BUSINESS_DATE, fakeBackend, jsonResponse, meRoute, renderApp } from '../../test/renderApp'

function loginAs(role: Role) {
  return {
    'POST /api/auth/login': {
      token: 'a.b.c',
      expiresAt: '2026-11-28T18:00:00Z',
      businessDate: BUSINESS_DATE,
      user: { id: 9, username: 'someone', fullName: 'Someone', role },
    },
  }
}

async function signIn() {
  await userEvent.type(screen.getByLabelText('Username'), 'someone')
  await userEvent.type(screen.getByLabelText('Password'), 'secret')
  await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
}

describe('Sign in', () => {
  it.each([
    ['SALESMAN', '/sales/new'],
    ['ADMIN', '/admin/orders'],
    ['PRODUCTION', '/production/runs'],
    ['AUDITOR', '/reports'],
    ['OWNER', '/reports'],
  ] as [Role, string][])('sends %s to %s', async (role, home) => {
    fakeBackend({ ...loginAs(role), ...meRoute(role), 'GET /api/orders': [], 'GET /api/customers': [], 'GET /api/products': [] })
    const router = renderApp('/login', null)

    await signIn()

    await screen.findByRole('button', { name: 'Sign out' })
    expect(router.state.location.pathname).toBe(home)
    expect(JSON.parse(sessionStorage.getItem('ota.session')!)).toMatchObject({ token: 'a.b.c', businessDate: BUSINESS_DATE })
  })

  it('shows the same message for a wrong password and stays on the page', async () => {
    fakeBackend({
      'POST /api/auth/login': () =>
        jsonResponse({ status: 401, error: 'Unauthorized', message: 'Invalid username or password', details: [] }, 401),
    })
    const router = renderApp('/login', null)

    await signIn()

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid username or password.')
    expect(router.state.location.pathname).toBe('/login')
  })

  it('asks for both fields before calling the server', async () => {
    const { calls } = fakeBackend({})
    renderApp('/login', null)

    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(screen.getByText('Enter your username.')).toBeInTheDocument()
    expect(screen.getByText('Enter your password.')).toBeInTheDocument()
    expect(calls).toHaveLength(0)
  })

  it('sends a signed-out visitor to the login page', async () => {
    fakeBackend({ ...meRoute('SALESMAN') })
    const signedOut = renderApp('/admin/orders', null)
    expect(signedOut.state.location.pathname).toBe('/login')
  })

  it('tells a salesman they cannot open the admin pages', async () => {
    fakeBackend({ ...meRoute('SALESMAN') })
    renderApp('/admin/orders', 'SALESMAN')

    expect(await screen.findByRole('heading', { name: 'Not allowed' })).toBeInTheDocument()
  })

  it('signs out: the session is cleared and the login page shows', async () => {
    fakeBackend({ ...meRoute('ADMIN'), 'GET /api/orders': [] })
    const router = renderApp('/admin/orders', 'ADMIN')

    await userEvent.click(await screen.findByRole('button', { name: 'Sign out' }))

    expect(sessionStorage.getItem('ota.session')).toBeNull()
    expect(router.state.location.pathname).toBe('/login')
  })
})

describe('the token', () => {
  it('goes with every request of a page opened straight after a reload', async () => {
    const { calls } = fakeBackend({ ...meRoute('SALESMAN'), 'GET /api/customers': [], 'GET /api/products': [] })
    renderApp('/sales/new', 'SALESMAN')

    await screen.findByText(/No customers are assigned to you/)

    expect(calls.map((call) => call.url)).toEqual(expect.arrayContaining(['/api/customers', '/api/products']))
    for (const call of calls) {
      expect(call.authorization, call.url).toBe('Bearer token-SALESMAN')
    }
  })

  it('a 401 during the session clears it and goes to the login page', async () => {
    fakeBackend({
      ...meRoute('ADMIN'),
      'GET /api/orders': () =>
        jsonResponse({ status: 401, error: 'Unauthorized', message: 'A valid login token is required', details: [] }, 401),
    })
    const router = renderApp('/admin/orders', 'ADMIN')

    await screen.findByText('Your session ended. Sign in again.')
    expect(router.state.location.pathname).toBe('/login')
    expect(sessionStorage.getItem('ota.session')).toBeNull()
  })
})
