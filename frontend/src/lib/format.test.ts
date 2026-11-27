import { describe, expect, it } from 'vitest'

import { formatDate, formatMoney, formatNumber, formatPercent, formatQty } from './format'

describe('formatMoney (UI guide 5)', () => {
  it('groups the Indian way with two decimals', () => {
    expect(formatMoney(7875)).toBe('7,875.00')
    expect(formatMoney(100000)).toBe('1,00,000.00')
    expect(formatMoney(12345678.9)).toBe('1,23,45,678.90')
    expect(formatMoney(0.5)).toBe('0.50')
  })

  it('adds the rupee sign for totals and uses the real minus sign', () => {
    expect(formatMoney(7875, { withSymbol: true })).toBe('₹7,875.00')
    expect(formatMoney(-3501.05)).toBe('−3,501.05')
    expect(formatMoney(-7875, { withSymbol: true })).toBe('−₹7,875.00')
  })
})

describe('formatQty', () => {
  it('trims trailing zeros and adds the unit', () => {
    expect(formatQty(120, 'kg')).toBe('120 kg')
    expect(formatQty(19.5, 'L')).toBe('19.5 L')
    expect(formatQty(150, 'pkt')).toBe('150 pkt')
    expect(formatQty(3.8, 'kg')).toBe('3.8 kg')
    expect(formatQty(0.1234, 'kg')).toBe('0.1234 kg')
  })

  it('formats a bare number for columns that name the unit', () => {
    expect(formatNumber(100000)).toBe('1,00,000')
    expect(formatNumber(88)).toBe('88')
  })
})

describe('formatPercent', () => {
  it('always shows the sign of a variance, with two decimals', () => {
    expect(formatPercent(5)).toBe('+5.00%')
    expect(formatPercent(-6)).toBe('−6.00%')
    expect(formatPercent(3.19)).toBe('+3.19%')
    expect(formatPercent(0)).toBe('0.00%')
  })
})

describe('formatDate', () => {
  it('writes an ISO date as "28 Nov 2026"', () => {
    expect(formatDate('2026-11-28')).toBe('28 Nov 2026')
    expect(formatDate('2026-04-01')).toBe('1 Apr 2026')
    expect(formatDate('2027-03-31T00:00:00Z')).toBe('31 Mar 2027')
  })
})
