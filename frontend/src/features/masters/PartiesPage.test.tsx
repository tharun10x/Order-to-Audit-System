import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'

import type { Customer, CustomerRequest, Supplier, SupplierRequest } from '../../api/types'
import { distributorB, retailerA } from '../../test/fixtures'
import { fakeBackend, jsonResponse, meRoute, renderApp } from '../../test/renderApp'

const customers = {
  'GET /api/customers?status=active': [retailerA, distributorB],
  'GET /api/customers?status=all': [retailerA, distributorB],
  'GET /api/salesmen': [
    { id: 3, username: 'salesman_a', fullName: 'Salesman A' },
    { id: 4, username: 'salesman_b', fullName: 'Salesman B' },
  ],
}

const flourMills: Supplier = { id: 1, name: 'Sample Flour Mills', gstin: null, stateCode: '33', phone: null, active: true }
const suppliers = {
  'GET /api/suppliers?status=active': [flourMills],
  'GET /api/suppliers?status=all': [flourMills],
}

describe('Customer form (R5.3, M1, M5)', () => {
  it('says what is missing and that the GSTIN does not match the state', async () => {
    const { calls } = fakeBackend({ ...meRoute('ADMIN'), ...customers })
    renderApp('/masters/customers/new', 'ADMIN')

    await userEvent.type(await screen.findByLabelText('GSTIN'), '29aaacd7654b1z2')
    expect(screen.getByLabelText('GSTIN')).toHaveValue('29AAACD7654B1Z2')
    await userEvent.selectOptions(screen.getByLabelText('State'), 'Tamil Nadu (33)')
    await userEvent.click(screen.getByRole('button', { name: 'Create customer' }))

    expect(screen.getByText('Enter the customer code, for example RET-C.')).toBeInTheDocument()
    expect(screen.getByText('Pick retailer or distributor.')).toBeInTheDocument()
    expect(screen.getByText('Pick the salesman who looks after this customer.')).toBeInTheDocument()
    expect(screen.getByLabelText('GSTIN')).toHaveAccessibleDescription(
      'This GSTIN starts with 29, but the state code is 33. Check the GSTIN or the state.')
    expect(calls.some((call) => call.method === 'POST')).toBe(false)
  })

  it('sends the chosen salesman and state, and empty optional fields as null', async () => {
    const created: Customer = {
      ...retailerA, id: 9, code: 'RET-C', name: 'Retailer C', gstin: null, address: null, salesmanId: 4, salesmanName: 'Salesman B',
    }
    // The backend lists the new customer once it is created
    let all = [retailerA, distributorB]
    const { calls } = fakeBackend({
      ...meRoute('ADMIN'),
      ...customers,
      'GET /api/customers?status=all': () => jsonResponse(all),
      'POST /api/customers': () => {
        all = [...all, created]
        return jsonResponse(created, 201)
      },
    })
    renderApp('/masters/customers/new', 'ADMIN')

    await userEvent.type(await screen.findByLabelText('Code'), 'ret-c')
    await userEvent.type(screen.getByLabelText('Name'), 'Retailer C')
    await userEvent.selectOptions(screen.getByLabelText('Type'), 'Retailer')
    await userEvent.selectOptions(screen.getByLabelText('Salesman'), 'Salesman B')
    await userEvent.selectOptions(screen.getByLabelText('State'), 'Tamil Nadu (33)')
    await userEvent.click(screen.getByRole('button', { name: 'Create customer' }))

    expect(await screen.findByText('Customer RET-C created. Salesman B can now take orders for it.')).toBeInTheDocument()
    const sent = calls.find((call) => call.method === 'POST')!.body as CustomerRequest
    expect(sent).toEqual({
      code: 'RET-C', name: 'Retailer C', type: 'RETAILER', stateCode: '33', gstin: null, address: null, phone: null, salesmanId: 4,
    })
  })

  it('shows the customer read-only to the auditor', async () => {
    const { calls } = fakeBackend({ ...meRoute('AUDITOR'), ...customers })
    renderApp('/masters/customers/2', 'AUDITOR')

    expect(await screen.findByRole('heading', { name: 'Distributor B' })).toBeInTheDocument()
    expect(screen.getAllByText('Karnataka (29)').length).toBeGreaterThan(0)
    expect(screen.getByText('IGST')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Save|Deactivate/ })).not.toBeInTheDocument()
    // The salesmen list is the admin's: the auditor's screen never asks for it
    expect(calls.some((call) => call.url === '/api/salesmen')).toBe(false)
    const tabs = screen.getByRole('navigation', { name: 'Masters' })
    expect(within(tabs).getAllByRole('link').map((link) => link.textContent)).toEqual(['Items', 'Recipes', 'Customers'])
  })
})

describe('Supplier form (R5.3, M4)', () => {
  it('needs the state with a GSTIN, and has no salesman', async () => {
    const { calls } = fakeBackend({ ...meRoute('ADMIN'), ...suppliers })
    renderApp('/masters/suppliers/new', 'ADMIN')

    await userEvent.type(await screen.findByLabelText('GSTIN'), '33AAACS1234B1Z9')
    await userEvent.click(screen.getByRole('button', { name: 'Create supplier' }))

    expect(screen.getByText('Enter the name as it should appear on documents.')).toBeInTheDocument()
    expect(screen.getByText('Pick the state as well: a GSTIN starts with its state code.')).toBeInTheDocument()
    expect(screen.queryByLabelText('Salesman')).not.toBeInTheDocument()
    expect(calls.some((call) => call.method === 'POST')).toBe(false)
  })

  it('edits a supplier and shows the backend refusal as a sentence', async () => {
    const { calls } = fakeBackend({
      ...meRoute('ADMIN'),
      ...suppliers,
      'PUT /api/suppliers/1': () => jsonResponse({
        status: 409, error: 'Conflict', details: [], message: 'An active supplier is already called Sample Oil Traders',
      }, 409),
    })
    renderApp('/masters/suppliers/1', 'ADMIN')

    const name = await screen.findByLabelText('Name')
    await userEvent.clear(name)
    await userEvent.type(name, 'Sample Oil Traders')
    await userEvent.type(screen.getByLabelText('Phone'), '9840012345')
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByText('An active supplier is already called Sample Oil Traders.')).toBeInTheDocument()
    expect(calls.find((call) => call.method === 'PUT')!.body as SupplierRequest)
      .toEqual({ name: 'Sample Oil Traders', stateCode: '33', gstin: null, phone: '9840012345' })
  })
})
