import type { Role } from '../api/types'
import { REPORT_ROLES } from '../lib/reportCatalog'

/**
 * R2 on the screen: which pages a role can open and which actions it sees. The backend enforces the same rules
 * (it refuses with 403), so this only keeps people from seeing buttons they cannot use.
 */
const READ_ACCOUNTS: Role[] = ['ADMIN', 'AUDITOR', 'OWNER']
const READ_OPERATIONS: Role[] = ['ADMIN', 'PRODUCTION', 'AUDITOR', 'OWNER']

export const PAGE_ROLES = {
  // R15: each role sees only the reports it may open (lib/reportCatalog); production has the operations ones
  reports: REPORT_ROLES,
  dashboard: ['OWNER', 'ADMIN'] as Role[],
  orders: READ_ACCOUNTS,
  invoices: READ_ACCOUNTS,
  payments: READ_ACCOUNTS,
  recordPayment: ['ADMIN'] as Role[],
  purchases: READ_OPERATIONS,
  recordPurchase: ['ADMIN', 'PRODUCTION'] as Role[],
  production: READ_OPERATIONS,
  newRun: ['PRODUCTION'] as Role[],
  stock: READ_OPERATIONS,
  // Masters (R5): read-only for everyone but the admin. Each list is open to the roles its backend list allows:
  // customers to the accounts roles, suppliers to whoever records purchases.
  masterItems: READ_OPERATIONS,
  masterRecipes: READ_OPERATIONS,
  masterCustomers: READ_ACCOUNTS,
  masterSuppliers: ['ADMIN', 'PRODUCTION'] as Role[],
  /** The new-item, new-customer, new-supplier and new-recipe-version pages. */
  manageMasters: ['ADMIN'] as Role[],
}

/** Actions inside pages. */
export const can = {
  /** Approve, reject, edit lines, raise invoices, dispatch, cancel invoices, record payments. */
  manageSales: (role: Role) => role === 'ADMIN',
  recordPurchase: (role: Role) => PAGE_ROLES.recordPurchase.includes(role),
  /** Create runs, enter actuals, post, cancel, add overheads (R8: production only). */
  runProduction: (role: Role) => role === 'PRODUCTION',
  /** Create, edit, deactivate and reactivate masters; save and activate recipe versions (R5: admin only). */
  manageMasters: (role: Role) => PAGE_ROLES.manageMasters.includes(role),
}

/** The masters area's tabs, in order; each role sees the ones it can open. */
const MASTERS_TABS = [
  { to: '/masters/items', label: 'Items', roles: PAGE_ROLES.masterItems },
  { to: '/masters/recipes', label: 'Recipes', roles: PAGE_ROLES.masterRecipes },
  { to: '/masters/customers', label: 'Customers', roles: PAGE_ROLES.masterCustomers },
  { to: '/masters/suppliers', label: 'Suppliers', roles: PAGE_ROLES.masterSuppliers },
]

export function mastersTabs(role: Role): { to: string; label: string }[] {
  return MASTERS_TABS.filter((tab) => tab.roles.includes(role))
}

/** The top bar's pages for each role, in order. */
export const NAV: Record<Role, { to: string; label: string }[]> = {
  ADMIN: [
    { to: '/orders', label: 'Orders' },
    { to: '/invoices', label: 'Invoices' },
    { to: '/payments', label: 'Payments' },
    { to: '/purchases', label: 'Purchases' },
    { to: '/production/runs', label: 'Production' },
    { to: '/stock', label: 'Stock' },
    { to: '/masters', label: 'Masters' },
    { to: '/reports', label: 'Reports' },
    { to: '/dashboard', label: 'Dashboard' },
  ],
  PRODUCTION: [
    { to: '/production/runs', label: 'Production runs' },
    { to: '/purchases', label: 'Purchases' },
    { to: '/stock', label: 'Stock' },
    { to: '/masters', label: 'Masters' },
    { to: '/reports', label: 'Reports' },
  ],
  AUDITOR: [
    { to: '/reports', label: 'Reports' },
    { to: '/orders', label: 'Orders' },
    { to: '/invoices', label: 'Invoices' },
    { to: '/payments', label: 'Payments' },
    { to: '/purchases', label: 'Purchases' },
    { to: '/production/runs', label: 'Production' },
    { to: '/stock', label: 'Stock' },
    { to: '/masters', label: 'Masters' },
  ],
  OWNER: [
    { to: '/dashboard', label: 'Dashboard' },
    { to: '/reports', label: 'Reports' },
    { to: '/orders', label: 'Orders' },
    { to: '/invoices', label: 'Invoices' },
    { to: '/payments', label: 'Payments' },
    { to: '/purchases', label: 'Purchases' },
    { to: '/production/runs', label: 'Production' },
    { to: '/stock', label: 'Stock' },
    { to: '/masters', label: 'Masters' },
  ],
  SALESMAN: [],
}
