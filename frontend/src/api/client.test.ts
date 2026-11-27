import { beforeEach, describe, expect, it, vi } from 'vitest'

import { fakeBackend, jsonResponse } from '../test/renderApp'
import { api, ApiError, configureApi, FORBIDDEN_MESSAGE, NETWORK_MESSAGE } from './client'

describe('the api client', () => {
  const onUnauthorized = vi.fn()

  beforeEach(() => {
    onUnauthorized.mockReset()
    configureApi({ getToken: () => 'the-token', onUnauthorized })
  })

  async function failure(promise: Promise<unknown>): Promise<ApiError> {
    try {
      await promise
    } catch (error) {
      return error as ApiError
    }
    throw new Error('expected the request to fail')
  }

  it('sends the token and the JSON body', async () => {
    const { fetchMock } = fakeBackend({ 'POST /api/orders': { id: 1 } })

    await expect(api.post('/api/orders', { customerId: 1 })).resolves.toEqual({ id: 1 })

    const init = fetchMock.mock.calls[0][1] as RequestInit
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer the-token')
    expect(init.body).toBe('{"customerId":1}')
  })

  it("keeps the backend's message and its detail lines", async () => {
    fakeBackend({
      'POST /api/orders/7/approve': () =>
        jsonResponse(
          {
            status: 409,
            error: 'Conflict',
            message: 'Order SO/2026-27/0002 cannot be approved: not enough finished-goods stock',
            details: ['MURUKKU200: ordered 120, available 88, short by 32, batches needed 1'],
          },
          409,
        ),
    })

    const error = await failure(api.post('/api/orders/7/approve'))

    expect(error.status).toBe(409)
    expect(error.message).toBe('Order SO/2026-27/0002 cannot be approved: not enough finished-goods stock.')
    expect(error.details).toEqual(['MURUKKU200: ordered 120, available 88, short by 32, batches needed 1'])
  })

  it('turns "Validation failed" into a sentence and keeps the field lines', async () => {
    fakeBackend({
      'POST /api/orders': () =>
        jsonResponse({ status: 400, error: 'Bad Request', message: 'Validation failed', details: ['lines: must not be empty'] }, 400),
    })

    const error = await failure(api.post('/api/orders', {}))

    expect(error.message).toBe('Some values need correcting:')
    expect(error.details).toEqual(['lines: must not be empty'])
  })

  it('ends the session on a 401', async () => {
    fakeBackend({
      'GET /api/orders': () =>
        jsonResponse({ status: 401, error: 'Unauthorized', message: 'A valid login token is required', details: [] }, 401),
    })

    const error = await failure(api.get('/api/orders'))

    expect(error.status).toBe(401)
    expect(onUnauthorized).toHaveBeenCalledOnce()
  })

  it('does not end a session on the login call: there a 401 is a wrong password', async () => {
    fakeBackend({
      'POST /api/auth/login': () =>
        jsonResponse({ status: 401, error: 'Unauthorized', message: 'Invalid username or password', details: [] }, 401),
    })

    const error = await failure(api.post('/api/auth/login', { username: 'admin', password: 'x' }, { anonymous: true }))

    expect(error.message).toBe('Invalid username or password.')
    expect(onUnauthorized).not.toHaveBeenCalled()
  })

  it('says "not allowed" on a role 403, but keeps a specific 403 sentence', async () => {
    fakeBackend({
      'GET /api/invoices': () =>
        jsonResponse({ status: 403, error: 'Forbidden', message: 'Your role is not allowed to do this', details: [] }, 403),
      'POST /api/orders': () =>
        jsonResponse({ status: 403, error: 'Forbidden', message: 'Customer Distributor B is not assigned to you', details: [] }, 403),
    })

    expect((await failure(api.get('/api/invoices'))).message).toBe(FORBIDDEN_MESSAGE)
    expect((await failure(api.post('/api/orders', {}))).message).toBe('Customer Distributor B is not assigned to you')
    expect(onUnauthorized).not.toHaveBeenCalled()
  })

  it('reports a dropped connection as a network error', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))))

    const error = await failure(api.get('/api/orders'))

    expect(error.isNetwork).toBe(true)
    expect(error.message).toBe(NETWORK_MESSAGE)
  })

  it('gives a plain sentence for a server error or a body that is not JSON', async () => {
    fakeBackend({ 'GET /api/orders': () => new Response('<html>Bad gateway</html>', { status: 502 }) })

    const error = await failure(api.get('/api/orders'))

    expect(error.status).toBe(502)
    expect(error.message).toMatch(/^The server could not finish this/)
  })
})
