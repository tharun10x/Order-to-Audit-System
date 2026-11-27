import type { Role } from '../api/types'

/** Where each role lands after signing in. */
export function roleHome(role: Role): string {
  switch (role) {
    case 'SALESMAN':
      return '/sales/new'
    case 'ADMIN':
      return '/orders'
    case 'PRODUCTION':
      return '/production/runs'
    case 'AUDITOR':
    case 'OWNER':
      return '/reports'
  }
}
