import type { Report, ReportCell, ReportTable } from '../../api/types'
import { Table, Td, Th } from '../../components/Table'
import { FLAG, formatCell, isFlagged, isNumeric } from './reportCells'

/**
 * R15.2: a report as the server built it. Each section is its own table, numbers right-aligned and tabular, the
 * totals row in bold. In a flagged row the flag reads "Flagged" and the variance is in danger (UI guide 2, 6).
 */
export function ReportPreview({ report, signedPercent }: { report: Report; signedPercent: string[] }) {
  return (
    <div className="flex flex-col gap-8">
      <div>
        <h2 className="text-lg leading-[26px] font-semibold">{report.title}</h2>
        <p className="num text-sm text-muted">{`${report.companyName} · ${report.periodLabel}`}</p>
      </div>
      {report.sections.map((section, index) => (
        <Section key={`${index}-${section.title}`} section={section} signedPercent={signedPercent} />
      ))}
    </div>
  )
}

function Section({ section, signedPercent }: { section: ReportTable; signedPercent: string[] }) {
  return (
    <section>
      <h3 className="mb-2 text-base font-semibold">{section.title}</h3>
      {section.rows.length === 0 ? (
        <p className="py-3 text-sm text-muted">Nothing in this period.</p>
      ) : null}
      <Table label={section.title}>
        <thead>
          <tr>
            {section.columns.map((column) => <Th key={column.key} numeric={isNumeric(column)} wrap>{column.header}</Th>)}
          </tr>
        </thead>
        <tbody>
          {section.rows.map((row, index) => (
            <Row key={index} section={section} row={row} signedPercent={signedPercent} />
          ))}
          {section.totals ? <Row section={section} row={section.totals} signedPercent={signedPercent} totals /> : null}
        </tbody>
      </Table>
    </section>
  )
}

function Row({ section, row, signedPercent, totals = false }: {
  section: ReportTable
  row: ReportCell[]
  signedPercent: string[]
  totals?: boolean
}) {
  const flagged = isFlagged(row)
  return (
    <tr className={totals ? 'font-semibold' : undefined} data-totals={totals || undefined}>
      {section.columns.map((column, index) => {
        const value = row[index]
        const signed = column.type === 'PERCENT' && signedPercent.includes(column.key)
        // Red means a problem: the flag itself and, in that row, the variance it is about
        const danger = flagged && (value === FLAG || signed)
        // A date, code or document number (no spaces) is never split over two lines; names may wrap
        const keepWhole = column.type === 'DATE' || (typeof value === 'string' && !value.includes(' '))
        return (
          <Td key={column.key} numeric={isNumeric(column)}
            className={`${danger ? 'font-semibold text-danger' : ''} ${keepWhole ? 'whitespace-nowrap' : ''}`}>
            {formatCell(column, value, signed)}
          </Td>
        )
      })}
    </tr>
  )
}
