import { describe, expect, it } from 'vitest'

import { reportSamples } from '../test/reports/samples'
import { REPORTS, reportsFor } from './reportCatalog'

describe('report catalogue (R2, R15)', () => {
  it('has a real sample for every report it offers', () => {
    for (const report of REPORTS) {
      expect(reportSamples[report.code], report.code).toBeDefined()
      expect(reportSamples[report.code].code).toBe(report.code)
    }
  })

  // The signed (variance) percentage columns are listed by exact key. If the backend renames or drops one, the
  // screen would quietly show a variance without its sign: this test fails instead.
  it.each(REPORTS.filter((report) => report.signedPercent).map((report) => [report.code, report.signedPercent!]))(
    '%s: every signed-percentage key is a PERCENT column of the real report',
    (code, keys) => {
      const columns = reportSamples[code].sections.flatMap((section) => section.columns)
      for (const key of keys) {
        const found = columns.filter((column) => column.key === key)
        expect(found.length, `column ${key} in ${code}`).toBeGreaterThan(0)
        expect(found.every((column) => column.type === 'PERCENT'), `column ${key} is PERCENT`).toBe(true)
      }
    },
  )

  it('leaves the other percentage columns (GST rates) unsigned', () => {
    const signed = new Set(REPORTS.flatMap((report) => report.signedPercent ?? []))
    const gstPercent = reportSamples['gst-summary'].sections.flatMap((section) => section.columns)
      .filter((column) => column.type === 'PERCENT')
    expect(gstPercent.map((column) => column.key)).toContain('rate')
    expect(gstPercent.some((column) => signed.has(column.key))).toBe(false)
  })

  it('offers production only the operations reports', () => {
    expect(reportsFor('PRODUCTION').map((report) => report.code)).toEqual(['stock-summary', 'stock-ledger', 'production-report'])
    expect(reportsFor('SALESMAN')).toEqual([])
    for (const role of ['ADMIN', 'AUDITOR', 'OWNER'] as const) {
      expect(reportsFor(role)).toHaveLength(8)
    }
  })
})
