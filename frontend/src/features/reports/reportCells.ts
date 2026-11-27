import type { ReportCell, ReportColumn } from '../../api/types'
import { formatDate, formatMoney, formatNumber, formatPercent, formatRate } from '../../lib/format'

/**
 * One report cell as text, by its column type (R15.2). The server sends plain values and the browser only
 * formats them (UI guide 5): nothing is rounded or added up here.
 */

/** What the production report puts in its Flag column (ProductionReport.FLAG); shown as the word "Flagged". */
export const FLAG = '⚑'

const MINUS = '−'

export function isNumeric(column: ReportColumn): boolean {
  return column.type !== 'TEXT' && column.type !== 'DATE'
}

/** A row is flagged when any of its text cells is the flag. */
export function isFlagged(row: ReportCell[]): boolean {
  return row.includes(FLAG)
}

export function formatCell(column: ReportColumn, value: ReportCell, signedPercent: boolean): string {
  if (value === null || value === '') {
    return ''
  }
  switch (column.type) {
    case 'MONEY':
      return formatMoney(Number(value))
    case 'QTY':
    case 'COUNT':
      return formatNumber(Number(value))
    case 'RATE':
      return formatRate(Number(value))
    case 'PERCENT':
      // A variance has a sign (+5.00%); a GST rate does not (its header already says %)
      return signedPercent ? formatPercent(Number(value)) : plainPercent(Number(value))
    case 'DATE':
      return formatDate(String(value))
    case 'TEXT':
      return value === FLAG ? 'Flagged' : String(value)
  }
}

function plainPercent(value: number): string {
  return `${value < 0 ? MINUS : ''}${Math.abs(value).toFixed(2)}`
}
