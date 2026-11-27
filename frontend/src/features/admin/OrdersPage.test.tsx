import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'

import { order, shortStockCheck } from '../../test/fixtures'
import { BUSINESS_DATE, fakeBackend, jsonResponse, meRoute, renderApp } from '../../test/renderApp'

const submitted = order()
const approved = order({
  status: 'APPROVED',
  approvedBy: 'admin',
  taxableValue: 4400,
  lines: [{ ...submitted.lines[0], approvedQty: 88, taxableValue: 4400 }],
})

describe('Admin orders', () => {
  it('lists orders with their value and filters by status', async () => {
    const { calls } = fakeBackend({
      ...meRoute('ADMIN'),
      'GET /api/orders': [submitted],
      'GET /api/orders?status=SUBMITTED': [submitted],
    })
    const router = renderApp('/admin/orders', 'ADMIN')

    const table = await screen.findByRole('table', { name: 'Orders' })
    const row = within(table).getByRole('row', { name: /SO\/2026-27\/0002/ })
    expect(within(row).getByText('Distributor B')).toBeInTheDocument()
    expect(within(row).getByText('6,000.00')).toBeInTheDocument()
    expect(within(row).getByText('Submitted')).toBeInTheDocument()

    await userEvent.selectOptions(screen.getByLabelText('Status'), 'SUBMITTED')
    await screen.findByRole('table', { name: 'Orders' })
    expect(router.state.location.search).toBe('?status=SUBMITTED')
    expect(calls.some((call) => call.url === '/api/orders?status=SUBMITTED')).toBe(true)
  })

  it('shows the shortage and approves the 88 packets that are in stock', async () => {
    let current = submitted
    const { calls } = fakeBackend({
      ...meRoute('ADMIN'),
      'GET /api/orders': () => jsonResponse([current]),
      'GET /api/orders/7': () => jsonResponse(current),
      'GET /api/orders/7/stock-check': shortStockCheck,
      'POST /api/orders/7/approve': () => {
        current = approved
        return jsonResponse(approved)
      },
    })
    renderApp('/admin/orders/7', 'ADMIN')

    const stock = await screen.findByRole('table', { name: 'Stock check' })
    expect(within(stock).getByText('Murukku 200 g')).toBeInTheDocument()
    expect(within(stock).getByText('Short by 32')).toHaveClass('text-danger')
    expect(screen.getByText(/Murukku 200 g: short by 32 packets/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Approve order' })).toBeDisabled()

    await userEvent.click(screen.getByRole('button', { name: 'Approve 88 packets' }))

    expect(await screen.findByText('Order approved for 88 packets.')).toBeInTheDocument()
    const approve = calls.find((call) => call.method === 'POST')!
    expect(approve.body).toEqual({ lines: [{ itemCode: 'MURUKKU200', approvedQty: 88 }] })
    // Now approved: the invoice can be created, dated the business date by default
    expect(await screen.findByLabelText('Invoice date')).toHaveValue(BUSINESS_DATE)
  })

  it('needs a reason to reject', async () => {
    const { calls } = fakeBackend({
      ...meRoute('ADMIN'),
      'GET /api/orders': [submitted],
      'GET /api/orders/7': submitted,
      'GET /api/orders/7/stock-check': shortStockCheck,
      'POST /api/orders/7/reject': () => jsonResponse({ ...submitted, status: 'REJECTED', remarks: 'Credit limit reached' }),
    })
    renderApp('/admin/orders/7', 'ADMIN')

    await userEvent.click(await screen.findByRole('button', { name: 'Reject order' }))
    expect(screen.getByText('Enter a reason, for example "Credit limit reached".')).toBeInTheDocument()
    expect(calls.some((call) => call.method === 'POST')).toBe(false)

    await userEvent.type(screen.getByLabelText('Reason'), 'Credit limit reached')
    await userEvent.click(screen.getByRole('button', { name: 'Reject order' }))
    expect(await screen.findByText('Order rejected.')).toBeInTheDocument()
    expect(calls.find((call) => call.method === 'POST')!.body).toEqual({ reason: 'Credit limit reached' })
  })

  it('shows the backend detail lines when approval is refused', async () => {
    fakeBackend({
      ...meRoute('ADMIN'),
      'GET /api/orders': [submitted],
      'GET /api/orders/7': submitted,
      'GET /api/orders/7/stock-check': shortStockCheck,
      'POST /api/orders/7/approve': () =>
        jsonResponse(
          {
            status: 409,
            error: 'Conflict',
            message: 'Order SO/2026-27/0002 cannot be approved: not enough finished-goods stock',
            details: ['MURUKKU200: ordered 120, approving 88, available 0, short by 88, batches needed 1'],
          },
          409,
        ),
    })
    renderApp('/admin/orders/7', 'ADMIN')

    await userEvent.click(await screen.findByRole('button', { name: 'Approve 88 packets' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('cannot be approved: not enough finished-goods stock.')
    expect(within(alert).getByText(/short by 88/)).toBeInTheDocument()
  })

  it('creates the invoice for an approved order', async () => {
    const { calls } = fakeBackend({
      ...meRoute('ADMIN'),
      'GET /api/orders': [approved],
      'GET /api/orders/7': approved,
      'POST /api/invoices': { id: 1, invoiceNo: 'INV/2026-27/0001', invoiceDate: BUSINESS_DATE, total: 4620 },
    })
    renderApp('/admin/orders/7', 'ADMIN')

    await userEvent.click(await screen.findByRole('button', { name: 'Create invoice' }))

    expect(await screen.findByText('Invoice INV/2026-27/0001 created.')).toBeInTheDocument()
    expect(calls.find((call) => call.method === 'POST')!.body).toEqual({ orderId: 7, invoiceDate: BUSINESS_DATE })
  })
})
