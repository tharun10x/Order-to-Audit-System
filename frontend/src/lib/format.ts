// UI guide section 5. The browser only formats: it never rounds or recomputes anything that is saved.

const MINUS = '−' // the real minus sign, not a hyphen

const money = new Intl.NumberFormat('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const quantity = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 4 })
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

/** 7875 → "7,875.00"; 100000 → "1,00,000.00". withSymbol for totals and single amounts: "₹7,875.00". */
export function formatMoney(amount: number, options: { withSymbol?: boolean } = {}): string {
  const sign = amount < 0 ? MINUS : ''
  const symbol = options.withSymbol ? '₹' : ''
  return `${sign}${symbol}${money.format(Math.abs(amount))}`
}

/** As the API sends it, trailing zeros trimmed, with the unit: "120 kg", "19.5 L", "150 pkt". */
export function formatQty(qty: number, unit: string): string {
  const sign = qty < 0 ? MINUS : ''
  return `${sign}${quantity.format(Math.abs(qty))} ${unit}`
}

/** A quantity in a table column whose header names the unit ("Ordered (pkt)"): "150", "1,00,000", "19.5". */
export function formatNumber(qty: number): string {
  const sign = qty < 0 ? MINUS : ''
  return `${sign}${quantity.format(Math.abs(qty))}`
}

/** Variances: 2 decimals with a sign, "+5.00%", "−6.00%"; zero has no sign. */
export function formatPercent(value: number): string {
  const sign = value > 0 ? '+' : value < 0 ? MINUS : ''
  return `${sign}${Math.abs(value).toFixed(2)}%`
}

/** "2026-11-28" (an ISO date from the API) → "28 Nov 2026". Read as a calendar date, so no time zone can shift it. */
export function formatDate(isoDate: string): string {
  const [year, month, day] = isoDate.slice(0, 10).split('-').map(Number)
  return `${day} ${MONTHS[month - 1]} ${year}`
}
