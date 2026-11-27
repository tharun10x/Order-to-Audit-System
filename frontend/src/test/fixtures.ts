import type {
  Customer, CustomerOutstanding, Invoice, Item, Order, Product, Run, RunLine, StockCheck,
} from '../api/types'

export const retailerA: Customer = {
  id: 1,
  code: 'RET-A',
  name: 'Retailer A',
  type: 'RETAILER',
  stateCode: '33',
  gstin: '33AAACR5055K1Z5',
  address: 'Tiruchirappalli, Tamil Nadu',
  phone: null,
  intraState: true,
}

export const distributorB: Customer = {
  id: 2,
  code: 'DIST-B',
  name: 'Distributor B',
  type: 'DISTRIBUTOR',
  stateCode: '29',
  gstin: '29AAACD7654B1Z2',
  address: 'Bengaluru, Karnataka',
  phone: null,
  intraState: false,
}

export const murukku: Product = {
  code: 'MURUKKU200',
  name: 'Murukku 200 g',
  sellingPrice: 50,
  hsnCode: '21069099',
  gstRate: 5,
  packetsPerCarton: 20,
}

export function order(overrides: Partial<Order> = {}): Order {
  return {
    id: 7,
    orderNo: 'SO/2026-27/0002',
    clientUuid: '00000000-0000-4000-8000-000000000002',
    customerId: 2,
    customerName: 'Distributor B',
    salesman: 'salesman_b',
    orderDate: '2026-11-28',
    status: 'SUBMITTED',
    remarks: null,
    approvedBy: null,
    approvedAt: null,
    taxableValue: 6000,
    invoiceNo: null,
    lines: [{ itemCode: 'MURUKKU200', itemName: 'Murukku 200 g', qty: 120, approvedQty: null, rate: 50, taxableValue: 6000 }],
    ...overrides,
  }
}

/** The benchmark: Distributor B wants 120, 88 are free. */
export const shortStockCheck: StockCheck = {
  orderId: 7,
  orderNo: 'SO/2026-27/0002',
  canApprove: false,
  lines: [{ itemCode: 'MURUKKU200', ordered: 120, onHand: 88, reserved: 0, available: 88, shortage: 32, batchesNeeded: 1 }],
}

// ---------------------------------------------------------------- milestone 2

export const items: Item[] = [
  { code: 'MURUKKU200', name: 'Murukku 200 g', kind: 'FG', baseUnit: 'pkt', units: ['pkt', 'ctn'], hasActiveBom: true },
  { code: 'CARTON', name: 'Carton (20 packets)', kind: 'PACKAGING', baseUnit: 'pc', units: ['pc'], hasActiveBom: false },
  { code: 'POUCH', name: '200 g pouch', kind: 'PACKAGING', baseUnit: 'pc', units: ['pc'], hasActiveBom: false },
  { code: 'FLOUR', name: 'Flour', kind: 'RAW', baseUnit: 'kg', units: ['kg', 'g'], hasActiveBom: false },
  { code: 'OIL', name: 'Cooking oil', kind: 'RAW', baseUnit: 'L', units: ['L', 'ml'], hasActiveBom: false },
  { code: 'SPICE', name: 'Salt and spice mix', kind: 'RAW', baseUnit: 'kg', units: ['kg', 'g'], hasActiveBom: false },
]

/** Retailer A's benchmark invoice, dispatched, nothing paid yet. */
export function invoice(overrides: Partial<Invoice> = {}): Invoice {
  return {
    id: 10,
    invoiceNo: 'INV/2026-27/0001',
    invoiceDate: '2026-11-28',
    orderId: 1,
    orderNo: 'SO/2026-27/0001',
    customerId: 1,
    seller: { name: 'Sample Murukku Foods', gstin: '33AAACO1234A1Z5', stateCode: '33', address: 'Tiruchirappalli, Tamil Nadu' },
    buyer: { name: 'Retailer A', gstin: '33AAACR5055K1Z5', stateCode: '33', address: 'Tiruchirappalli, Tamil Nadu' },
    placeOfSupply: '33',
    supplyType: 'INTRA',
    taxableValue: 7500,
    cgst: 187.5,
    sgst: 187.5,
    igst: 0,
    taxTotal: 375,
    total: 7875,
    status: 'POSTED',
    creditNoteNo: null,
    dispatchNo: 'DC/2026-27/0001',
    dispatchDate: '2026-11-28',
    paid: 0,
    outstanding: 7875,
    paymentStatus: 'UNPAID',
    payments: [],
    lines: [{ itemCode: 'MURUKKU200', itemName: 'Murukku 200 g', hsnCode: '21069099', qty: 150, rate: 50, taxableValue: 7500, gstRate: 5, cgst: 187.5, sgst: 187.5, igst: 0 }],
    ...overrides,
  }
}

export const retailerAOutstanding: CustomerOutstanding = {
  customerId: 1,
  customerName: 'Retailer A',
  asOf: '2026-11-28',
  totalOutstanding: 8400,
  buckets: { '0-30': 8400, '31-60': 0, '61-90': 0, '90+': 0 },
  invoices: [
    { invoiceId: 10, invoiceNo: 'INV/2026-27/0001', invoiceDate: '2026-11-28', total: 7875, paid: 0, outstanding: 7875, paymentStatus: 'UNPAID', days: 0, ageingBucket: '0-30' },
    { invoiceId: 11, invoiceNo: 'INV/2026-27/0002', invoiceDate: '2026-11-28', total: 525, paid: 0, outstanding: 525, paymentStatus: 'UNPAID', days: 0, ageingBucket: '0-30' },
  ],
}

function runLine(itemCode: string, itemName: string, kind: RunLine['kind'], unit: string, plannedQty: number,
  actualQty: number | null, scrapQty: number, variancePct: number | null, flagged: boolean): RunLine {
  return { itemCode, itemName, kind, unit, plannedQty, actualQty, scrapQty, unitCost: null, value: null, variancePct, flagged }
}

/** The benchmark run with its actuals saved, still a draft. */
export function draftRun(): Run {
  return {
    id: 5, runNo: 'RUN/2026-27/0001', productCode: 'MURUKKU200', productName: 'Murukku 200 g', runDate: '2026-11-28',
    status: 'DRAFT', batches: 1, plannedOutput: 200, actualOutput: 188, yieldVariancePct: -6, yieldFlagged: true,
    tolerancePct: 3, postedAt: null, postedBy: null, overheads: [], costing: null,
    lines: [
      runLine('FLOUR', 'Flour', 'RAW', 'kg', 40, 40, 0, 0, false),
      runLine('OIL', 'Cooking oil', 'RAW', 'L', 10, 10.5, 0, 5, true),
      runLine('SPICE', 'Salt and spice mix', 'RAW', 'kg', 1.2, 1.2, 0, 0, false),
      runLine('POUCH', '200 g pouch', 'PACKAGING', 'pc', 200, 194, 6, -3, false),
      runLine('CARTON', 'Carton (20 packets)', 'PACKAGING', 'pc', 10, 9, 0, -10, true),
    ],
  }
}

/** The benchmark run posted, with labour and gas/power added. */
export function postedRun(): Run {
  const run = draftRun()
  return {
    ...run,
    status: 'POSTED',
    postedAt: '2026-11-28T04:30:00Z',
    postedBy: 'production',
    lines: [
      runLine('FLOUR', 'Flour', 'RAW', 'kg', 40, 40, 0, 0, false),
      runLine('OIL', 'Cooking oil', 'RAW', 'L', 10, 10.5, 0, 5, true),
      runLine('SPICE', 'Salt and spice mix', 'RAW', 'kg', 1.2, 1.2, 0, 0, false),
      runLine('POUCH', '200 g pouch', 'PACKAGING', 'pc', 188, 194, 6, 3.19, true),
      runLine('CARTON', 'Carton (20 packets)', 'PACKAGING', 'pc', 9, 9, 0, 0, false),
    ],
    overheads: [
      { id: 1, type: 'LABOUR', amount: 1200, note: null },
      { id: 2, type: 'POWER_GAS', amount: 600, note: null },
    ],
    costing: {
      materialCost: 3435, packagingCost: 620, overheadsByType: { LABOUR: 1200, POWER_GAS: 600, OTHER: 0 },
      overheadTotal: 1800, totalCost: 5855, costPerPacket: 31.1436, costPerPacketRounded: 31.14, sellingPrice: 50,
      marginPerPacket: 18.8564, marginPerPacketRounded: 18.86, marginPct: 37.71,
    },
  }
}
