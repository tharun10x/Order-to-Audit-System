import type { Role } from '../api/types'

/**
 * The reports the Reports page offers (R15), with the roles the backend allows for each (R2: the same sets as
 * REPORTS_READ and OPERATIONS_READ in security/Roles). my-sales is the salesman's own page, not listed here.
 */
export type ReportCode =
  | 'sales-register' | 'gst-summary' | 'purchase-register' | 'stock-summary' | 'stock-ledger'
  | 'production-report' | 'collections' | 'outstanding'

export interface ReportEntry {
  code: ReportCode
  label: string
  roles: Role[]
  /** The stock ledger is for one item: ?itemCode= is required. */
  needsItem?: boolean
  /** Outstanding is a position on one day (R15.6): only the "as of" date (to) is asked for. */
  asOf?: boolean
  /**
   * The PERCENT columns that are variances, shown with a sign (+5.00%, UI guide 5). Every other PERCENT column
   * (a GST rate) is shown without one. Listed by exact column key, as the backend sends them; a test checks each
   * key against a real report.
   */
  signedPercent?: string[]
}

const ACCOUNTS: Role[] = ['ADMIN', 'AUDITOR', 'OWNER']
const OPERATIONS: Role[] = ['ADMIN', 'PRODUCTION', 'AUDITOR', 'OWNER']

export const REPORTS: ReportEntry[] = [
  { code: 'sales-register', label: 'Sales register', roles: ACCOUNTS },
  { code: 'gst-summary', label: 'GST summary', roles: ACCOUNTS },
  { code: 'purchase-register', label: 'Purchase register', roles: ACCOUNTS },
  { code: 'stock-summary', label: 'Stock summary', roles: OPERATIONS },
  { code: 'stock-ledger', label: 'Stock ledger', roles: OPERATIONS, needsItem: true },
  { code: 'production-report', label: 'Production report', roles: OPERATIONS, signedPercent: ['yieldVariance', 'variance'] },
  { code: 'collections', label: 'Collections', roles: ACCOUNTS },
  { code: 'outstanding', label: 'Outstanding with ageing', roles: ACCOUNTS, asOf: true },
]

export function reportsFor(role: Role): ReportEntry[] {
  return REPORTS.filter((report) => report.roles.includes(role))
}

/** Any role that can open at least one report. */
export const REPORT_ROLES: Role[] = OPERATIONS
