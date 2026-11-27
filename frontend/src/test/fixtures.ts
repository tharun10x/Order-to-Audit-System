import type { Customer, Order, Product, StockCheck } from '../api/types'

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
