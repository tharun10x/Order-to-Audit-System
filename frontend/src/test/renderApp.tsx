import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { vi } from 'vitest'

import { configureApi } from '../api/client'
import type { Role } from '../api/types'
import { setUpApi } from '../auth/apiSetup'
import { AuthProvider } from '../auth/AuthProvider'
import { clearSession, saveSession } from '../auth/session'
import { routes } from '../routes/router'

export const BUSINESS_DATE = '2026-11-28'

const USERS: Record<Role, { id: number; username: string; fullName: string }> = {
  ADMIN: { id: 1, username: 'admin', fullName: 'Admin' },
  PRODUCTION: { id: 2, username: 'production', fullName: 'Production Manager' },
  SALESMAN: { id: 3, username: 'salesman_a', fullName: 'Salesman A' },
  AUDITOR: { id: 5, username: 'auditor', fullName: 'Auditor' },
  OWNER: { id: 6, username: 'owner', fullName: 'Owner' },
}

export function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

export interface Call {
  method: string
  url: string
  body: unknown
  /** The Authorization header as sent, or null. */
  authorization: string | null
}

/** A fake backend: answer by "METHOD /path" (query string included). Every request is recorded in calls. */
export type Handler = (call: Call) => Response | Promise<Response>

export function fakeBackend(routes: Record<string, Handler | unknown>) {
  const calls: Call[] = []
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    const method = init?.method ?? 'GET'
    const body = typeof init?.body === 'string' ? JSON.parse(init.body) : undefined
    const headers = (init?.headers ?? {}) as Record<string, string>
    const call = { method, url, body, authorization: headers.Authorization ?? null }
    calls.push(call)
    const route = routes[`${method} ${url}`]
    if (route === undefined) {
      return jsonResponse({ status: 404, error: 'Not Found', message: `No fake for ${method} ${url}`, details: [] }, 404)
    }
    return typeof route === 'function' ? (route as Handler)(call) : jsonResponse(route)
  })
  vi.stubGlobal('fetch', fetchMock)
  return { calls, fetchMock }
}

/** The whole app (real routes, real auth) at a URL, signed in as the given role, or signed out. */
export function renderApp(path: string, role: Role | null) {
  clearSession()
  if (role) {
    saveSession({
      token: `token-${role}`,
      expiresAt: '2026-11-28T18:00:00Z',
      businessDate: BUSINESS_DATE,
      user: { ...USERS[role], role },
    })
  }
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  // As main.tsx does. Reset first, so no test can lean on a set-up left over from an earlier one
  configureApi({ getToken: () => null, onUnauthorized: () => {} })
  setUpApi(queryClient, (target) => void router.navigate(target))
  render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>,
  )
  return router
}

/** /api/auth/me for a signed-in role, as the AuthProvider asks on start-up. */
export function meRoute(role: Role) {
  return { 'GET /api/auth/me': { ...USERS[role], role, businessDate: BUSINESS_DATE } }
}
