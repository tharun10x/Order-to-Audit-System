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

// ---------------------------------------------------------------- invoices (R10, R11)

export type InvoiceStatus = 'POSTED' | 'CANCELLED'
export type SupplyType = 'INTRA' | 'INTER'
export type InvoicePaymentStatus = 'UNPAID' | 'PARTIALLY_PAID' | 'PAID'
export type PaymentMode = 'CASH' | 'UPI' | 'BANK' | 'CHEQUE'
export type PaymentStatus = 'POSTED' | 'REVERSED'

/** For the buyer, stateCode is the place of supply. */
export interface Party {
  name: string
  gstin: string | null
  stateCode: string
  address: string | null
}

export interface InvoiceLine {
  itemCode: string
  itemName: string
  hsnCode: string
  qty: number
  rate: number
  taxableValue: number
  gstRate: number
  cgst: number
  sgst: number
  igst: number
}

/** A payment allocated to the invoice; amount is the part that settled this invoice. */
export interface InvoicePayment {
  paymentId: number
  receivedOn: string
  mode: PaymentMode
  reference: string | null
  amount: number
  status: PaymentStatus
}

/** paid, outstanding and paymentStatus are null for a cancelled invoice. */
export interface Invoice {
  id: number
  invoiceNo: string
  invoiceDate: string
  orderId: number
  orderNo: string
  customerId: number
  seller: Party
  buyer: Party
  placeOfSupply: string
  supplyType: SupplyType
  taxableValue: number
  cgst: number
  sgst: number
  igst: number
  taxTotal: number
  total: number
  status: InvoiceStatus
  creditNoteNo: string | null
  dispatchNo: string | null
  dispatchDate: string | null
  paid: number | null
  outstanding: number | null
  paymentStatus: InvoicePaymentStatus | null
  payments: InvoicePayment[]
  lines: InvoiceLine[]
}

export interface Dispatch {
  id: number
  dispatchNo: string
  invoiceId: number
  invoiceNo: string
  orderId: number
  dispatchDate: string
  vehicleNo: string | null
  lines: { itemCode: string; qty: number }[]
}

// ---------------------------------------------------------------- payments (R12)

export interface Payment {
  id: number
  customerId: number
  customerName: string
  receivedOn: string
  amount: number
  mode: PaymentMode
  reference: string | null
  status: PaymentStatus
  /** outstanding and paymentStatus describe the invoice now; null once cancelled. */
  allocations: { invoiceId: number; invoiceNo: string; amount: number; outstanding: number | null; paymentStatus: InvoicePaymentStatus | null }[]
}

export const AGEING_BUCKETS = ['0-30', '31-60', '61-90', '90+'] as const

export interface CustomerOutstanding {
  customerId: number
  customerName: string
  asOf: string
  totalOutstanding: number
  buckets: Record<string, number>
  invoices: {
    invoiceId: number
    invoiceNo: string
    invoiceDate: string
    total: number
    paid: number
    outstanding: number
    paymentStatus: InvoicePaymentStatus
    days: number
    ageingBucket: string
  }[]
}

// ---------------------------------------------------------------- masters for purchases and production

export interface Supplier {
  id: number
  name: string
  gstin: string | null
  stateCode: string | null
}

export type ItemKind = 'RAW' | 'PACKAGING' | 'FG'

/** units: what the item can be entered in, base unit first (R3.3, decided by the backend). */
export interface Item {
  code: string
  name: string
  kind: ItemKind
  baseUnit: string
  units: string[]
  hasActiveBom: boolean
}

// ---------------------------------------------------------------- purchases (R7)

export interface Purchase {
  id: number
  supplierId: number
  supplierName: string
  purchaseDate: string
  billNo: string
  status: 'POSTED' | 'REVERSED'
  total: number
  /** qty and rate are in unit, the item's base unit. */
  lines: { itemCode: string; itemName: string; qty: number; unit: string; rate: number; amount: number }[]
}

export interface PurchaseRequest {
  supplierId: number
  purchaseDate: string
  billNo: string
  /** qty and rate in the unit named on the line, as on the supplier's bill. */
  lines: { itemCode: string; qty: number; uomCode: string; rate: number }[]
}

// ---------------------------------------------------------------- production (R8, R13)

export type RunStatus = 'DRAFT' | 'POSTED' | 'CANCELLED' | 'REVERSED'
export type OverheadType = 'LABOUR' | 'POWER_GAS' | 'OTHER'

export interface RunSummary {
  id: number
  runNo: string
  productCode: string
  runDate: string
  status: RunStatus
  batches: number
  plannedOutput: number
  actualOutput: number | null
  yieldVariancePct: number | null
  yieldFlagged: boolean
  costPerPacket: number | null
}

/** Quantities in unit, the item's base unit. variancePct is null until it can be worked out. */
export interface RunLine {
  itemCode: string
  itemName: string
  kind: ItemKind
  unit: string
  plannedQty: number
  actualQty: number | null
  scrapQty: number
  unitCost: number | null
  value: number | null
  variancePct: number | null
  flagged: boolean
}

export interface RunCosting {
  materialCost: number
  packagingCost: number
  overheadsByType: Record<OverheadType, number>
  overheadTotal: number
  totalCost: number
  costPerPacket: number
  costPerPacketRounded: number
  sellingPrice: number
  marginPerPacket: number
  marginPerPacketRounded: number
  marginPct: number | null
}

export interface Run {
  id: number
  runNo: string
  productCode: string
  productName: string
  runDate: string
  status: RunStatus
  batches: number
  plannedOutput: number
  actualOutput: number | null
  yieldVariancePct: number | null
  yieldFlagged: boolean
  tolerancePct: number
  postedAt: string | null
  postedBy: string | null
  lines: RunLine[]
  overheads: { id: number; type: OverheadType; amount: number; note: string | null }[]
  /** null until the run is posted. */
  costing: RunCosting | null
}

export interface ActualsRequest {
  actualOutput: number
  lines: { itemCode: string; actualQty: number; scrapQty: number; uomCode: string }[]
}

// ---------------------------------------------------------------- stock (R6, R15)

export interface Balance {
  itemId: number
  itemCode: string
  itemName: string
  kind: ItemKind
  unit: string
  qtyOnHand: number
  stockValue: number
  avgCost: number | null
}

export type MovementType = 'OPENING' | 'RECEIPT' | 'ISSUE' | 'PRODUCTION_OUTPUT' | 'SALE' | 'RETURN' | 'ADJUSTMENT' | 'REVERSAL'
export type SourceType = 'OPENING' | 'PURCHASE' | 'PRODUCTION_RUN' | 'DISPATCH' | 'CREDIT_NOTE' | 'ADJUSTMENT'

/** qty and value are signed: + in, − out. invoiceId is set for a dispatch or credit note; a run's id is sourceId. */
export interface LedgerRow {
  id: number
  movementDate: string
  movementType: MovementType
  sourceType: SourceType
  sourceId: number | null
  documentNo: string
  invoiceId: number | null
  qty: number
  unitCost: number
  value: number
  runningQty: number
  runningValue: number
  remarks: string | null
}
