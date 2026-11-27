import type { OrderStatus } from '../api/types'

/** UI guide section 6: status as a word (never colour alone), coloured by meaning. */
export const STATUS_LOOK: Record<OrderStatus, { label: string; className: string }> = {
  SUBMITTED: { label: 'Submitted', className: 'text-ink' },
  APPROVED: { label: 'Approved', className: 'text-primary' },
  INVOICED: { label: 'Invoiced', className: 'text-primary' },
  DISPATCHED: { label: 'Dispatched', className: 'text-success' },
  REJECTED: { label: 'Rejected', className: 'text-muted' },
  CANCELLED: { label: 'Cancelled', className: 'text-muted' },
}

export function statusLabel(status: OrderStatus): string {
  return STATUS_LOOK[status].label
}
