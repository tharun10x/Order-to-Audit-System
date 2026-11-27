// The backend's JSON, as its controllers and DTOs send it. BigDecimal values arrive as JSON numbers.

export type Role = 'SALESMAN' | 'ADMIN' | 'PRODUCTION' | 'AUDITOR' | 'OWNER'

export interface User {
  id: number
  username: string
  fullName: string
  role: Role
}

/** POST /api/auth/login. businessDate is "today" for the business (A17): default for every date input. */
export interface LoginResponse {
  token: string
  expiresAt: string
  businessDate: string
  user: User
}

/** GET /api/auth/me */
export interface Me extends User {
  businessDate: string
}

/** The backend's error body for every 4xx and 5xx. */
export interface ErrorResponse {
  status: number
  error: string
  message: string
  details: string[]
}

export interface Customer {
  id: number
  code: string
  name: string
  type: 'RETAILER' | 'DISTRIBUTOR'
  stateCode: string
  gstin: string | null
  address: string | null
  phone: string | null
  /** true: same state as the factory, so CGST + SGST. false: IGST (R10.3). */
  intraState: boolean
}

export interface Product {
  code: string
  name: string
  sellingPrice: number
  hsnCode: string
  gstRate: number
  packetsPerCarton: number | null
}

export type OrderStatus = 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'INVOICED' | 'DISPATCHED' | 'CANCELLED'

export const ORDER_STATUSES: OrderStatus[] = ['SUBMITTED', 'APPROVED', 'REJECTED', 'INVOICED', 'DISPATCHED', 'CANCELLED']

export interface OrderLine {
  itemCode: string
  itemName: string
  qty: number
  approvedQty: number | null
  rate: number
  taxableValue: number
}

export interface Order {
  id: number
  orderNo: string
  clientUuid: string
  customerId: number
  customerName: string
  salesman: string
  orderDate: string
  status: OrderStatus
  remarks: string | null
  approvedBy: string | null
  approvedAt: string | null
  /** Before GST; approved quantities once approved. */
  taxableValue: number
  invoiceNo: string | null
  lines: OrderLine[]
}

export interface StockCheckLine {
  itemCode: string
  ordered: number
  onHand: number
  reserved: number
  available: number
  shortage: number
  /** null when the product has no active recipe. */
  batchesNeeded: number | null
}

export interface StockCheck {
  orderId: number
  orderNo: string
  canApprove: boolean
  lines: StockCheckLine[]
}

/** The part of POST /api/invoices' answer this milestone uses. */
export interface InvoiceSummary {
  id: number
  invoiceNo: string
  invoiceDate: string
  total: number
}

export type Unit = 'pkt' | 'ctn'

export interface CreateOrderRequest {
  customerId: number
  clientUuid: string
  lines: { itemCode: string; qty: number; uomCode: Unit }[]
}
