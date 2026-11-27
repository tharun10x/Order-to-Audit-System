import type { Dashboard, Report } from '../../api/types'
import collections from './collections.json'
import dashboard from './dashboard.json'
import gstSummary from './gst-summary.json'
import mySales from './my-sales.json'
import outstanding from './outstanding.json'
import productionReport from './production-report.json'
import purchaseRegister from './purchase-register.json'
import salesRegister from './sales-register.json'
import stockLedger from './stock-ledger.json'
import stockSummary from './stock-summary.json'

/**
 * Real answers of the backend for the benchmark month (brief section 5, steps 1 to 8 done), captured from
 * GET /api/reports/{code}?from=2026-11-01&to=2026-11-30&format=json (stock ledger for FLOUR), my-sales as
 * salesman_a, and GET /api/dashboard as the owner on 28 Nov 2026. Tests read them as the screens would.
 */
export const reportSamples: Record<string, Report> = {
  'sales-register': salesRegister as Report,
  'gst-summary': gstSummary as Report,
  'purchase-register': purchaseRegister as Report,
  'stock-summary': stockSummary as Report,
  'stock-ledger': stockLedger as Report,
  'production-report': productionReport as Report,
  collections: collections as Report,
  outstanding: outstanding as Report,
  'my-sales': mySales as Report,
}

export const dashboardSample = dashboard as Dashboard
