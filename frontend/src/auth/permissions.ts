import type { Role } from '../api/types'

/**
 * R2 on the screen: which pages a role can open and which actions it sees. The backend enforces the same rules
 * (it refuses with 403), so this only keeps people from seeing buttons they cannot use.
 */
const READ_ACCOUNTS: Role[] = ['ADMIN', 'AUDITOR', 'OWNER']
const READ_OPERATIONS: Role[] = ['ADMIN', 'PRODUCTION', 'AUDITOR', 'OWNER']

export const PAGE_ROLES = {
  reports: ['AUDITOR', 'OWNER'] as Role[],
  orders: READ_ACCOUNTS,
  invoices: READ_ACCOUNTS,
  payments: READ_ACCOUNTS,
  recordPayment: ['ADMIN'] as Role[],
  purchases: READ_OPERATIONS,
  recordPurchase: ['ADMIN', 'PRODUCTION'] as Role[],
  production: READ_OPERATIONS,
  newRun: ['PRODUCTION'] as Role[],
  stock: READ_OPERATIONS,
}

/** Actions inside pages. */
export const can = {
  /** Approve, reject, edit lines, raise invoices, dispatch, cancel invoices, record payments. */
  manageSales: (role: Role) => role === 'ADMIN',
  recordPurchase: (role: Role) => PAGE_ROLES.recordPurchase.includes(role),
  /** Create runs, enter actuals, post, cancel, add overheads (R8: production only). */
  runProduction: (role: Role) => role === 'PRODUCTION',
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
  ],
  PRODUCTION: [
    { to: '/production/runs', label: 'Production runs' },
    { to: '/purchases', label: 'Purchases' },
    { to: '/stock', label: 'Stock' },
  ],
  AUDITOR: [
    { to: '/reports', label: 'Reports' },
    { to: '/orders', label: 'Orders' },
    { to: '/invoices', label: 'Invoices' },
    { to: '/payments', label: 'Payments' },
    { to: '/purchases', label: 'Purchases' },
    { to: '/production/runs', label: 'Production' },
    { to: '/stock', label: 'Stock' },
  ],
  OWNER: [
    { to: '/reports', label: 'Reports' },
    { to: '/orders', label: 'Orders' },
    { to: '/invoices', label: 'Invoices' },
    { to: '/payments', label: 'Payments' },
    { to: '/purchases', label: 'Purchases' },
    { to: '/production/runs', label: 'Production' },
    { to: '/stock', label: 'Stock' },
  ],
  SALESMAN: [],
}
