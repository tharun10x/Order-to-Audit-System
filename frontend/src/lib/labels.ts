import type {
  CustomerType, InvoicePaymentStatus, InvoiceStatus, ItemKind, MovementType, OverheadType, PaymentMode, PaymentStatus, RunStatus,
} from '../api/types'

/** Words for the backend's codes (UI guide section 8: write for the person, not the system). */

export const PAYMENT_MODE: Record<PaymentMode, string> = {
  CASH: 'Cash',
  UPI: 'UPI',
  BANK: 'Bank transfer',
  CHEQUE: 'Cheque',
}

export const PAYMENT_STATUS: Record<InvoicePaymentStatus, string> = {
  UNPAID: 'Unpaid',
  PARTIALLY_PAID: 'Partly paid',
  PAID: 'Paid',
}

export const OVERHEAD_TYPE: Record<OverheadType, string> = {
  LABOUR: 'Labour',
  POWER_GAS: 'Gas / power',
  OTHER: 'Other',
}

export const ITEM_KIND: Record<ItemKind, string> = {
  RAW: 'Raw materials',
  PACKAGING: 'Packaging',
  FG: 'Finished goods',
}

export const MOVEMENT: Record<MovementType, string> = {
  OPENING: 'Opening stock',
  RECEIPT: 'Receipt',
  ISSUE: 'Issue to production',
  PRODUCTION_OUTPUT: 'Production output',
  SALE: 'Dispatch',
  RETURN: 'Return',
  ADJUSTMENT: 'Adjustment',
  REVERSAL: 'Reversal',
}

/** Status words and their colour (UI guide 6): completed states in success, cancelled in muted. */
export const INVOICE_STATUS: Record<InvoiceStatus, { label: string; className: string }> = {
  POSTED: { label: 'Posted', className: 'text-success' },
  CANCELLED: { label: 'Cancelled', className: 'text-muted' },
}

export const RUN_STATUS: Record<RunStatus, { label: string; className: string }> = {
  DRAFT: { label: 'Draft', className: 'text-ink' },
  POSTED: { label: 'Posted', className: 'text-success' },
  CANCELLED: { label: 'Cancelled', className: 'text-muted' },
  REVERSED: { label: 'Reversed', className: 'text-muted' },
}

export const PAYMENT_RECORD_STATUS: Record<PaymentStatus, { label: string; className: string }> = {
  POSTED: { label: 'Posted', className: 'text-success' },
  REVERSED: { label: 'Reversed', className: 'text-muted' },
}

/** One item's kind, for a form or a table cell. */
export const ITEM_KIND_ONE: Record<ItemKind, string> = {
  RAW: 'Raw material',
  PACKAGING: 'Packaging',
  FG: 'Finished good',
}

export const CUSTOMER_TYPE: Record<CustomerType, string> = {
  RETAILER: 'Retailer',
  DISTRIBUTOR: 'Distributor',
}

/** Base units (factor 1, M8) with their names, for the item form. */
export const BASE_UNIT: Record<string, string> = {
  kg: 'kg (kilogram)',
  L: 'L (litre)',
  pc: 'pc (piece)',
  pkt: 'pkt (packet)',
}
