import { describe, expect, it } from 'vitest'

import { estimateOrder } from './gstEstimate'

const murukku = (packets: number) => ({ packets, rate: 50, gstRate: 5 })

describe('estimateOrder (same rules as the invoice, R10.3 and R10.4)', () => {
  it('splits CGST and SGST within Tamil Nadu: the Retailer A benchmark', () => {
    expect(estimateOrder([murukku(150)], true)).toEqual({ taxable: 7500, cgst: 187.5, sgst: 187.5, igst: 0, total: 7875 })
  })

  it('charges IGST to another state: the Distributor B benchmark', () => {
    expect(estimateOrder([murukku(120)], false)).toEqual({ taxable: 6000, cgst: 0, sgst: 0, igst: 300, total: 6300 })
    expect(estimateOrder([murukku(88)], false).total).toBe(4620)
  })

  it('rounds each half up per line, like the backend', () => {
    // 2.5% of 10.20 is exactly 0.255: each half rounds up to 0.26
    const line = { packets: 1, rate: 10.2, gstRate: 5 }
    expect(estimateOrder([line], true)).toMatchObject({ cgst: 0.26, sgst: 0.26, total: 10.72 })
    expect(estimateOrder([line, line], true)).toMatchObject({ taxable: 20.4, cgst: 0.52 })
  })

  it('is zero for an empty order', () => {
    expect(estimateOrder([], true).total).toBe(0)
  })
})
