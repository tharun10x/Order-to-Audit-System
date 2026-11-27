import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'

import { distributorB, retailerA, retailerAOutstanding } from '../../test/fixtures'
import { fakeBackend, jsonResponse, meRoute, renderApp, type Call } from '../../test/renderApp'

function backend() {
  return fakeBackend({
    ...meRoute('ADMIN'),
    'GET /api/customers': [distributorB, retailerA],
    'GET /api/customers/1/outstanding': retailerAOutstanding,
    'POST /api/payments': (call: Call) => {
      const body = call.body as { amount: string; allocations: { invoiceId: number; amount: string }[] }
      return jsonResponse({
        id: 1, customerId: 1, customerName: 'Retailer A', receivedOn: '2026-11-28', amount: Number(body.amount),
        mode: 'UPI', reference: null, status: 'POSTED',
        allocations: [{ invoiceId: 10, invoiceNo: 'INV/2026-27/0001', amount: 4000, outstanding: 3875, paymentStatus: 'PARTIALLY_PAID' }],
      }, 201)
    },
  })
}

const allocated = () => screen.getByRole('status')
const submit = () => screen.getByRole('button', { name: 'Record payment' })

describe('Record payment (R12.6: allocations must equal the amount)', () => {
  it('prefills the chosen invoice with the amount and allows submitting only when they are equal', async () => {
    const { calls } = backend()
    renderApp('/payments/new?customer=1&invoice=10', 'ADMIN')

    await userEvent.type(await screen.findByLabelText('Amount received (₹)'), '4000')
    expect(screen.getByLabelText('Allocate to INV/2026-27/0001')).toHaveValue('4000')
    expect(allocated()).toHaveTextContent('Allocated ₹4,000.00 of ₹4,000.00')
    expect(submit()).toBeEnabled()

    // Allocating less than the amount blocks the button and says why
    const toFirst = screen.getByLabelText('Allocate to INV/2026-27/0001')
    await userEvent.clear(toFirst)
    await userEvent.type(toFirst, '3999.99')
    expect(allocated()).toHaveTextContent('Allocated ₹3,999.99 of ₹4,000.00. They must be equal')
    expect(allocated()).toHaveClass('text-danger')
    expect(submit()).toBeDisabled()

    // Split over two invoices, exactly to the paisa
    await userEvent.clear(toFirst)
    await userEvent.type(toFirst, '3475.50')
    await userEvent.type(screen.getByLabelText('Allocate to INV/2026-27/0002'), '524.50')
    expect(allocated()).toHaveTextContent('Allocated ₹4,000.00 of ₹4,000.00')

    await userEvent.click(submit())
    expect(await screen.findByText(/Payment of ₹4,000.00 recorded/)).toBeInTheDocument()
    expect(calls.find((call) => call.method === 'POST')!.body).toEqual({
      customerId: 1,
      receivedOn: '2026-11-28',
      amount: '4000',
      mode: 'UPI',
      reference: null,
      allocations: [
        { invoiceId: 10, amount: '3475.50' },
        { invoiceId: 11, amount: '524.50' },
      ],
    })
  })

  it('refuses an allocation above what is outstanding', async () => {
    backend()
    renderApp('/payments/new?customer=1&invoice=11', 'ADMIN')

    await userEvent.type(await screen.findByLabelText('Amount received (₹)'), '600')

    const row = screen.getByLabelText('Allocate to INV/2026-27/0002').closest('tr')!
    expect(within(row).getByText('More than is outstanding.')).toBeInTheDocument()
    expect(submit()).toBeDisabled()
  })

  it('shows the backend refusal as sent', async () => {
    fakeBackend({
      ...meRoute('ADMIN'),
      'GET /api/customers': [retailerA],
      'GET /api/customers/1/outstanding': retailerAOutstanding,
      'POST /api/payments': () => jsonResponse({
        status: 400, error: 'Bad Request', details: [],
        message: 'Invoice INV/2026-27/0001 is dated 2026-11-28, after the payment was received on 2026-11-01 (advances are not supported)',
      }, 400),
    })
    renderApp('/payments/new?customer=1&invoice=10', 'ADMIN')
    await userEvent.type(await screen.findByLabelText('Amount received (₹)'), '100')

    await userEvent.click(submit())

    expect(await screen.findByRole('alert')).toHaveTextContent('advances are not supported')
  })
})
