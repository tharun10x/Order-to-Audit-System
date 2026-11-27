import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import type { CreateOrderRequest } from '../../api/types'
import { distributorB, murukku, order, retailerA } from '../../test/fixtures'
import { fakeBackend, jsonResponse, meRoute, renderApp, type Call } from '../../test/renderApp'

function backend(createOrder: (call: Call) => Response | Promise<Response>) {
  return fakeBackend({
    ...meRoute('SALESMAN'),
    'GET /api/customers': [retailerA, distributorB],
    'GET /api/products': [murukku],
    'GET /api/orders': [],
    'POST /api/orders': createOrder,
  })
}

async function pickRetailerA() {
  await userEvent.click(await screen.findByRole('button', { name: /Retailer A/ }))
}

describe('New order (salesman)', () => {
  it('converts cartons to packets and estimates CGST + SGST within Tamil Nadu', async () => {
    backend(() => jsonResponse(order(), 201))
    renderApp('/sales/new', 'SALESMAN')
    await pickRetailerA()

    await userEvent.click(screen.getByRole('radio', { name: 'Cartons' }))
    const quantity = screen.getByRole('textbox', { name: 'Cartons' })
    await userEvent.clear(quantity)
    await userEvent.type(quantity, '7')
    await userEvent.click(screen.getByRole('button', { name: 'One more carton' }))
    expect(screen.getByText('8 ctn = 160 pkt (20 per carton)')).toBeInTheDocument()

    const total = screen.getByRole('region', { name: 'Estimated total' })
    expect(within(total).getByText('₹8,000.00')).toBeInTheDocument() // taxable 160 x 50
    expect(within(total).getAllByText('₹200.00')).toHaveLength(2) // CGST and SGST
    expect(within(total).queryByText('IGST')).not.toBeInTheDocument()
    expect(within(total).getByText('₹8,400.00')).toBeInTheDocument()
    expect(within(total).getByText('Estimate. The invoice is final.')).toBeInTheDocument()
  })

  it('estimates IGST for a customer in another state', async () => {
    backend(() => jsonResponse(order(), 201))
    renderApp('/sales/new', 'SALESMAN')
    await userEvent.click(await screen.findByRole('button', { name: /Distributor B/ }))
    await userEvent.type(screen.getByRole('textbox', { name: 'Packets' }), '120')

    const total = screen.getByRole('region', { name: 'Estimated total' })
    expect(within(total).getByText('IGST')).toBeInTheDocument()
    expect(within(total).getByText('₹300.00')).toBeInTheDocument()
    expect(within(total).getByText('₹6,300.00')).toBeInTheDocument()
  })

  it('cannot be submitted without a quantity', async () => {
    backend(() => jsonResponse(order(), 201))
    renderApp('/sales/new', 'SALESMAN')
    await pickRetailerA()

    expect(screen.getByRole('button', { name: 'Submit order' })).toBeDisabled()
    expect(screen.getByText('Enter a quantity to submit.')).toBeInTheDocument()
  })

  it('sends the same client UUID again after a network error, and a new one for the next order', async () => {
    let attempts = 0
    const { calls } = backend(() => {
      attempts += 1
      if (attempts === 1) {
        return Promise.reject(new TypeError('Failed to fetch'))
      }
      return jsonResponse(order({ orderNo: 'SO/2026-27/0003', customerName: 'Retailer A' }), 201)
    })
    renderApp('/sales/new', 'SALESMAN')
    await pickRetailerA()
    await userEvent.type(screen.getByRole('textbox', { name: 'Packets' }), '150')

    await userEvent.click(screen.getByRole('button', { name: 'Submit order' }))
    expect(await screen.findByText(/Not sent: no connection/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Submit order again' }))
    expect(await screen.findByText('Order SO/2026-27/0003 sent to the factory.')).toBeInTheDocument()

    const sent = calls.filter((call) => call.method === 'POST').map((call) => call.body as CreateOrderRequest)
    expect(sent).toHaveLength(2)
    expect(sent[1].clientUuid).toBe(sent[0].clientUuid)
    expect(sent[0]).toMatchObject({ customerId: 1, lines: [{ itemCode: 'MURUKKU200', qty: 150, uomCode: 'pkt' }] })

    // The draft starts again, under a new UUID
    expect(screen.getByRole('heading', { name: 'Pick a customer' })).toBeInTheDocument()
    const next = JSON.parse(sessionStorage.getItem('ota.orderDraft')!) as { clientUuid: string }
    expect(next.clientUuid).not.toBe(sent[0].clientUuid)
  })

  it('changing the order after a failed send makes it a new order with a new UUID', async () => {
    const reply = vi
      .fn()
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValue(jsonResponse(order(), 201))
    const { calls } = backend(reply)
    renderApp('/sales/new', 'SALESMAN')
    await pickRetailerA()
    await userEvent.type(screen.getByRole('textbox', { name: 'Packets' }), '10')
    await userEvent.click(screen.getByRole('button', { name: 'Submit order' }))
    await screen.findByText(/Not sent: no connection/)

    await userEvent.click(screen.getByRole('button', { name: 'One more packet' }))
    await userEvent.click(screen.getByRole('button', { name: 'Submit order' }))
    await screen.findByText(/sent to the factory/)

    const [first, second] = calls.filter((call) => call.method === 'POST').map((call) => call.body as CreateOrderRequest)
    expect(second.clientUuid).not.toBe(first.clientUuid)
    expect(second.lines[0].qty).toBe(11)
  })
})
