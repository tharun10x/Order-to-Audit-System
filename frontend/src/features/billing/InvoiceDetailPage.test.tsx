import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { invoice } from '../../test/fixtures'
import { fakeBackend, jsonResponse, meRoute, renderApp } from '../../test/renderApp'

describe('Invoice detail', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('needs a reason to cancel, says what will happen, then shows the credit note', async () => {
    const { calls } = fakeBackend({
      ...meRoute('ADMIN'),
      'GET /api/invoices/10': invoice(),
      'POST /api/invoices/10/cancel': () => jsonResponse(invoice({ status: 'CANCELLED', creditNoteNo: 'CN/2026-27/0001', outstanding: null })),
    })
    renderApp('/invoices/10', 'ADMIN')

    await userEvent.click(await screen.findByRole('button', { name: 'Cancel invoice' }))
    expect(screen.getByText('Enter a reason, for example "Wrong quantity billed".')).toBeInTheDocument()
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()

    await userEvent.type(screen.getByLabelText('Reason'), 'Wrong quantity billed')
    await userEvent.click(screen.getByRole('button', { name: 'Cancel invoice' }))
    const confirm = screen.getByRole('alertdialog')
    expect(confirm).toHaveTextContent('A credit note for ₹7,875.00 is created')
    expect(confirm).toHaveTextContent('The 150 packets dispatched as DC/2026-27/0001 on 28 Nov 2026 return to stock')
    expect(calls.some((call) => call.method === 'POST')).toBe(false)

    await userEvent.click(within(confirm).getByRole('button', { name: 'Yes, cancel INV/2026-27/0001' }))
    expect(await screen.findByText('Invoice cancelled. Credit note CN/2026-27/0001 created.')).toBeInTheDocument()
    expect(calls.find((call) => call.method === 'POST')!.body).toEqual({ reason: 'Wrong quantity billed' })
  })

  it("shows the backend's refusal when the invoice has payments", async () => {
    fakeBackend({
      ...meRoute('ADMIN'),
      'GET /api/invoices/10': invoice({ paid: 4000, outstanding: 3875, paymentStatus: 'PARTIALLY_PAID' }),
      'POST /api/invoices/10/cancel': () => jsonResponse({
        status: 409, error: 'Conflict', details: [],
        message: 'Invoice INV/2026-27/0001 has payments. They must be reversed before it can be cancelled',
      }, 409),
    })
    renderApp('/invoices/10', 'ADMIN')
    await userEvent.type(await screen.findByLabelText('Reason'), 'Duplicate')
    await userEvent.click(screen.getByRole('button', { name: 'Cancel invoice' }))
    await userEvent.click(screen.getByRole('button', { name: 'Yes, cancel INV/2026-27/0001' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('has payments. They must be reversed before it can be cancelled.')
  })

  it('downloads the PDF under the file name the server gives', async () => {
    fakeBackend({
      ...meRoute('ADMIN'),
      'GET /api/invoices/10': invoice(),
      'GET /api/invoices/10/pdf': () => new Response('%PDF-1.4', {
        status: 200,
        headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': 'inline; filename="INV-2026-27-0001.pdf"' },
      }),
    })
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:pdf'), revokeObjectURL: vi.fn() }))
    const saved: string[] = []
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      saved.push(this.download)
    })
    renderApp('/invoices/10', 'ADMIN')

    await userEvent.click(await screen.findByRole('button', { name: 'Download PDF' }))

    await vi.waitFor(() => expect(saved).toEqual(['INV-2026-27-0001.pdf']))
  })

  it('offers a WhatsApp link when the device cannot share files', async () => {
    fakeBackend({
      ...meRoute('ADMIN'),
      'GET /api/invoices/10': invoice(),
      'GET /api/invoices/10/pdf': () => new Response('%PDF-1.4', {
        status: 200, headers: { 'Content-Disposition': 'inline; filename="INV-2026-27-0001.pdf"' },
      }),
    })
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:pdf'), revokeObjectURL: vi.fn() }))
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    renderApp('/invoices/10', 'ADMIN')

    await userEvent.click(await screen.findByRole('button', { name: 'Share invoice' }))

    const link = await screen.findByRole('link', { name: 'send the invoice details on WhatsApp' })
    expect(link).toHaveAttribute('href',
      `https://wa.me/?text=${encodeURIComponent('Invoice INV/2026-27/0001 for Retailer A: ₹7,875.00.')}`)
  })

  it('records the dispatch dated the business date', async () => {
    const { calls } = fakeBackend({
      ...meRoute('ADMIN'),
      'GET /api/invoices/10': invoice({ dispatchNo: null, dispatchDate: null }),
      'POST /api/dispatches': { id: 1, dispatchNo: 'DC/2026-27/0001', invoiceId: 10, invoiceNo: 'INV/2026-27/0001', orderId: 1, dispatchDate: '2026-11-28', vehicleNo: 'TN45', lines: [] },
    })
    renderApp('/invoices/10', 'ADMIN')

    await userEvent.type(await screen.findByLabelText('Vehicle number (optional)'), 'TN45')
    await userEvent.click(screen.getByRole('button', { name: 'Record dispatch' }))

    expect(await screen.findByText('Dispatched as DC/2026-27/0001.')).toBeInTheDocument()
    expect(calls.find((call) => call.method === 'POST')!.body).toEqual({ invoiceId: 10, dispatchDate: '2026-11-28', vehicleNo: 'TN45' })
  })

  it('shows an auditor the invoice without any action', async () => {
    fakeBackend({ ...meRoute('AUDITOR'), 'GET /api/invoices/10': invoice({ dispatchNo: null, dispatchDate: null }) })
    renderApp('/invoices/10', 'AUDITOR')

    expect(await screen.findByText('Sample Murukku Foods')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Download PDF' })).toBeInTheDocument()
    for (const action of ['Cancel invoice', 'Record dispatch']) {
      expect(screen.queryByRole('button', { name: action })).not.toBeInTheDocument()
    }
    expect(screen.queryByRole('link', { name: 'Record payment' })).not.toBeInTheDocument()
  })
})
