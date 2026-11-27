import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { Role } from '../../api/types'
import { items } from '../../test/fixtures'
import { fakeBackend, jsonResponse, meRoute, renderApp } from '../../test/renderApp'
import { reportSamples } from '../../test/reports/samples'

const MONTH = 'from=2026-11-01&to=2026-11-30'
const json = (code: string, extra = '') => `GET /api/reports/${code}?${MONTH}${extra}&format=json`
const ALL = ['Sales register', 'GST summary', 'Purchase register', 'Stock summary', 'Stock ledger', 'Production report',
  'Collections', 'Outstanding with ageing']

/** Every report of the month answers with its real sample. */
const reports = Object.fromEntries(
  Object.entries(reportSamples).filter(([code]) => code !== 'stock-ledger').map(([code, report]) => [json(code), report]),
)

const options = (select: HTMLElement) => within(select).getAllByRole('option').map((option) => option.textContent)

describe('Reports page (R2, R15)', () => {
  it.each([
    ['AUDITOR', ALL],
    ['OWNER', ALL],
    ['ADMIN', ALL],
    ['PRODUCTION', ['Stock summary', 'Stock ledger', 'Production report']],
  ] as [Role, string[]][])('offers %s only the reports it may open', async (role, expected) => {
    fakeBackend({ ...meRoute(role), ...reports })
    renderApp('/reports', role)

    expect(options(await screen.findByLabelText('Report'))).toEqual(expected)
  })

  it('defaults to the business month and previews it', async () => {
    const { calls } = fakeBackend({ ...meRoute('AUDITOR'), ...reports })
    renderApp('/reports', 'AUDITOR')

    expect(await screen.findByLabelText('From')).toHaveValue('2026-11-01')
    expect(screen.getByLabelText('To')).toHaveValue('2026-11-30')
    await screen.findByRole('heading', { name: 'Sales register' })
    expect(calls.map((call) => call.url)).toContain(`/api/reports/sales-register?${MONTH}&format=json`)
  })

  it('asks for one "as of" day for outstanding, and shows the server\'s period label', async () => {
    const { calls } = fakeBackend({ ...meRoute('OWNER'), ...reports })
    renderApp('/reports?report=outstanding', 'OWNER')

    expect(await screen.findByLabelText('As of')).toHaveValue('2026-11-30')
    expect(screen.queryByLabelText('From')).not.toBeInTheDocument()
    expect(await screen.findByText(/As of 30-Nov-2026/)).toBeInTheDocument()
    expect(calls.map((call) => call.url)).toContain(`/api/reports/outstanding?${MONTH}&format=json`)
  })

  it('needs an item for the stock ledger before it asks the server', async () => {
    const { calls } = fakeBackend({
      ...meRoute('PRODUCTION'),
      ...reports,
      'GET /api/items': items,
      [json('stock-ledger', '&itemCode=FLOUR')]: reportSamples['stock-ledger'],
    })
    renderApp('/reports', 'PRODUCTION')

    await userEvent.selectOptions(await screen.findByLabelText('Report'), 'Stock ledger')
    expect(screen.getByLabelText('Item')).toHaveAccessibleDescription('Pick the item whose ledger you want to see.')
    expect(screen.getByRole('button', { name: 'Download CSV' })).toBeDisabled()
    expect(calls.some((call) => call.url.startsWith('/api/reports/stock-ledger'))).toBe(false)

    await userEvent.selectOptions(screen.getByLabelText('Item'), 'Flour')
    expect(await screen.findByRole('table', { name: 'Movements of FLOUR' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Download CSV' })).toBeEnabled()
  })

  it('previews every section as its own table with a bold totals row', async () => {
    fakeBackend({ ...meRoute('AUDITOR'), ...reports })
    renderApp('/reports', 'AUDITOR')

    for (const name of ['Invoices and credit notes', 'Customer-wise sales', 'Product-wise sales']) {
      expect(await screen.findByRole('table', { name })).toBeInTheDocument()
    }
    const invoices = screen.getByRole('table', { name: 'Invoices and credit notes' })
    const totals = invoices.querySelector('tr[data-totals]')!
    expect(totals).toHaveClass('font-semibold')
    expect(totals).toHaveTextContent('7,875.00')
    // Numbers right-aligned and tabular
    expect(within(totals as HTMLElement).getByText('7,875.00')).toHaveClass('num', 'text-right')
  })

  it('shows flagged variances in danger with the word, and GST rates without a sign', async () => {
    fakeBackend({ ...meRoute('AUDITOR'), ...reports })
    renderApp('/reports?report=production-report', 'AUDITOR')

    const runs = await screen.findByRole('table', { name: 'Runs' })
    const run = within(runs).getByRole('row', { name: /^RUN\/2026-27\/0001/ })
    expect(within(run).getByText('−6.00%')).toHaveClass('text-danger', 'font-semibold')
    expect(within(run).getByText('Flagged')).toHaveClass('text-danger', 'font-semibold')
    expect(within(run).getByText('31.14')).toBeInTheDocument()
    const consumption = screen.getByRole('table', { name: 'RUN/2026-27/0001 consumption' })
    expect(within(within(consumption).getByRole('row', { name: /^OIL/ })).getByText('+5.00%')).toHaveClass('text-danger')
    // Within tolerance: signed, not flagged, not red
    const flour = within(consumption).getByRole('row', { name: /^FLOUR/ })
    expect(within(flour).getByText('0.00%')).not.toHaveClass('text-danger')
    expect(within(flour).queryByText('Flagged')).not.toBeInTheDocument()

    await userEvent.selectOptions(screen.getByLabelText('Report'), 'GST summary')
    const b2b = await screen.findByRole('table', { name: 'GSTR-1 B2B' })
    expect(within(b2b).getAllByText('5.00').length).toBeGreaterThan(0)
    expect(within(b2b).queryByText('+5.00%')).not.toBeInTheDocument()
  })
})

describe('Report downloads (R15.1)', () => {
  let saved: { name: string; href: string }[]

  beforeEach(() => {
    saved = []
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: () => 'blob:report', revokeObjectURL: () => {} }))
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      saved.push({ name: this.download, href: this.href })
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it.each([
    ['Download CSV', 'csv', 'text/csv'],
    ['Download Excel', 'xlsx', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'],
    ['Download PDF', 'pdf', 'application/pdf'],
  ])('%s asks for format=%s and saves under the server\'s file name', async (label, format, type) => {
    const serverName = `sales-register_2026-11-01_2026-11-30_from-server.${format}`
    const { calls } = fakeBackend({
      ...meRoute('AUDITOR'),
      ...reports,
      [`GET /api/reports/sales-register?${MONTH}&format=${format}`]: () => new Response('file contents', {
        status: 200,
        headers: { 'Content-Type': type, 'Content-Disposition': `attachment; filename="${serverName}"` },
      }),
    })
    renderApp('/reports', 'AUDITOR')

    await screen.findByRole('heading', { name: 'Sales register' })
    await userEvent.click(screen.getByRole('button', { name: label }))

    await vi.waitFor(() => expect(saved).toEqual([{ name: serverName, href: 'blob:report' }]))
    const download = calls.find((call) => call.url.endsWith(`format=${format}`))!
    expect(download.authorization).toBe('Bearer token-AUDITOR')
  })

  it('shows the server\'s refusal as a sentence', async () => {
    fakeBackend({
      ...meRoute('AUDITOR'),
      ...reports,
      [`GET /api/reports/sales-register?${MONTH}&format=pdf`]: () =>
        jsonResponse({ status: 400, error: 'Bad Request', message: "'from' (2026-12-01) must not be after 'to' (2026-11-30)", details: [] }, 400),
    })
    renderApp('/reports', 'AUDITOR')

    await screen.findByRole('heading', { name: 'Sales register' })
    await userEvent.click(screen.getByRole('button', { name: 'Download PDF' }))
    expect(await screen.findByText("'from' (2026-12-01) must not be after 'to' (2026-11-30).")).toBeInTheDocument()
    expect(saved).toEqual([])
  })
})
