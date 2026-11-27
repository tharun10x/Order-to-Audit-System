import type { ReactNode, TdHTMLAttributes, ThHTMLAttributes } from 'react'

/**
 * UI guide section 6, Tables: header 600 with a 2 px ink rule, rows with 1 px rule lines and 12 px vertical
 * padding, no zebra, numbers right-aligned and tabular. Wide tables scroll inside their panel.
 */
export function Table({ children, label }: { children: ReactNode; label: string }) {
  return (
    <div className="overflow-x-auto">
      <table aria-label={label} className="w-full border-collapse text-sm">
        {children}
      </table>
    </div>
  )
}

interface CellProps {
  numeric?: boolean
}

export function Th({ numeric = false, className = '', ...rest }: ThHTMLAttributes<HTMLTableCellElement> & CellProps) {
  return (
    <th
      scope="col"
      {...rest}
      className={`border-b-2 border-ink px-3 py-3 font-semibold whitespace-nowrap ${numeric ? 'text-right' : 'text-left'} ${className}`}
    />
  )
}

export function Td({ numeric = false, className = '', ...rest }: TdHTMLAttributes<HTMLTableCellElement> & CellProps) {
  return (
    <td
      {...rest}
      className={`border-b border-rule px-3 py-3 align-top ${numeric ? 'num text-right whitespace-nowrap' : 'text-left'} ${className}`}
    />
  )
}
