/**
 * Typed amounts and quantities, checked as text. The browser never rounds money it sends (UI guide 5): an amount
 * goes to the server exactly as typed, and comparisons ("allocated = amount") are done in whole paise, so floating
 * point never enters into it.
 */

const MONEY = /^\d+(\.\d{1,2})?$/
const QUANTITY = /^\d+(\.\d{1,4})?$/

/** "4000" → 400000, "3875.5" → 387550, anything that is not an amount with at most 2 decimals → null. */
export function toPaise(text: string): number | null {
  const value = text.trim()
  if (!MONEY.test(value)) {
    return null
  }
  const [rupees, fraction = ''] = value.split('.')
  return Number(rupees) * 100 + Number(fraction.padEnd(2, '0'))
}

/** Whole paise from an amount the API sent (a JSON number with at most 2 decimals). */
export function paiseOf(amount: number): number {
  return Math.round(amount * 100)
}

export function isAmount(text: string): boolean {
  return toPaise(text) !== null
}

/** A quantity: at most 4 decimals, like the database (R4). */
export function isQuantity(text: string): boolean {
  return QUANTITY.test(text.trim())
}

/** Amount text from an API amount, for prefilling a field: 3875 → "3875.00". */
export function amountText(amount: number): string {
  return amount.toFixed(2)
}
