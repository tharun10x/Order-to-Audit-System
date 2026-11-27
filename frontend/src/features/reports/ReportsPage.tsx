import { Download } from 'lucide-react'
import { useState } from 'react'
import { useSearchParams } from 'react-router'

import { useItems } from '../../api/operations'
import { downloadReport, useReport, type DownloadFormat, type ReportParams } from '../../api/reports'
import { useSession } from '../../auth/authContext'
import { Button } from '../../components/Button'
import { Field, TextInput } from '../../components/Field'
import { PageHeader, Select } from '../../components/Layout'
import { ErrorBox } from '../../components/Messages'
import { Panel } from '../../components/Panel'
import { firstOfMonth, lastOfMonth } from '../../lib/dates'
import { ITEM_KIND } from '../../lib/labels'
import { reportsFor, type ReportEntry } from '../../lib/reportCatalog'
import { saveFile } from '../../lib/saveFile'
import { ReportPreview } from './ReportPreview'

const DOWNLOADS: { format: DownloadFormat; label: string }[] = [
  { format: 'csv', label: 'Download CSV' },
  { format: 'xlsx', label: 'Download Excel' },
  { format: 'pdf', label: 'Download PDF' },
]

/**
 * R15: the month's reports. Pick a report (only those the role may open, R2), a period that defaults to the
 * business month (A17), and for the stock ledger an item. The preview is the server's JSON; the downloads are the
 * server's CSV, Excel and PDF files under the server's file name. The choices live in the URL, so a link or a
 * reload keeps them.
 */
export function ReportsPage() {
  const session = useSession()
  const allowed = reportsFor(session.user.role)
  const [params, setParams] = useSearchParams()
  const entry = allowed.find((report) => report.code === params.get('report')) ?? allowed[0]
  const from = params.get('from') ?? firstOfMonth(session.businessDate)
  const to = params.get('to') ?? lastOfMonth(session.businessDate)
  const itemCode = params.get('item') ?? ''

  function change(name: string, value: string) {
    const next = new URLSearchParams(params)
    next.set(name, value)
    setParams(next, { replace: true })
  }

  // Outstanding is "as of" a day (R15.6): its period is that day's month, so "from" can never pass "to"
  const period: ReportParams = {
    from: entry.asOf ? firstOfMonth(to) : from,
    to,
    itemCode: entry.needsItem ? itemCode : undefined,
  }
  const errors = {
    from: !entry.asOf && !from ? 'Pick the first day of the period.' : null,
    to: !to ? (entry.asOf ? 'Pick the day to report on.' : 'Pick the last day of the period.') : null,
    order: !entry.asOf && from && to && from > to ? 'The first day must not be after the last day.' : null,
    item: entry.needsItem && !itemCode ? 'Pick the item whose ledger you want to see.' : null,
  }
  const ready = Object.values(errors).every((error) => !error)

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Reports" />
      <Panel>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Field label="Report">
            {({ id }) => (
              <Select id={id} value={entry.code} onChange={(event) => change('report', event.target.value)}>
                {allowed.map((report) => <option key={report.code} value={report.code}>{report.label}</option>)}
              </Select>
            )}
          </Field>
          {entry.asOf ? null : (
            <Field label="From" error={errors.from ?? errors.order ?? undefined}>
              {({ id, describedBy }) => (
                <TextInput id={id} type="date" aria-describedby={describedBy} value={from}
                  onChange={(event) => change('from', event.target.value)} />
              )}
            </Field>
          )}
          <Field label={entry.asOf ? 'As of' : 'To'} error={errors.to ?? undefined}>
            {({ id, describedBy }) => (
              <TextInput id={id} type="date" aria-describedby={describedBy} value={to}
                onChange={(event) => change('to', event.target.value)} />
            )}
          </Field>
          {entry.needsItem ? <ItemPicker value={itemCode} error={errors.item} onChange={(code) => change('item', code)} /> : null}
        </div>
        <Downloads entry={entry} period={period} ready={ready} />
      </Panel>
      {ready ? <Preview entry={entry} period={period} /> : null}
    </div>
  )
}

function ItemPicker({ value, error, onChange }: { value: string; error: string | null; onChange: (code: string) => void }) {
  const items = useItems()
  return (
    <Field label="Item" error={error ?? undefined}>
      {({ id, describedBy }) => (
        <Select id={id} aria-describedby={describedBy} value={value} disabled={!items.data} onChange={(event) => onChange(event.target.value)}>
          <option value="">{items.data ? 'Pick an item' : 'Loading items…'}</option>
          {(['RAW', 'PACKAGING', 'FG'] as const).map((kind) => (
            <optgroup key={kind} label={ITEM_KIND[kind]}>
              {(items.data ?? []).filter((item) => item.kind === kind).map((item) => (
                <option key={item.code} value={item.code}>{item.name}</option>
              ))}
            </optgroup>
          ))}
        </Select>
      )}
    </Field>
  )
}

/** The server's files. A disabled button says why nearby (UI guide 6). */
function Downloads({ entry, period, ready }: { entry: ReportEntry; period: ReportParams; ready: boolean }) {
  const [pending, setPending] = useState<DownloadFormat | null>(null)
  const [error, setError] = useState<unknown>(null)

  async function save(format: DownloadFormat) {
    setPending(format)
    setError(null)
    try {
      saveFile(await downloadReport(entry.code, period, format))
    } catch (failure) {
      setError(failure)
    } finally {
      setPending(null)
    }
  }

  return (
    <div className="mt-6 flex flex-col gap-3">
      <div className="flex flex-wrap gap-3">
        {DOWNLOADS.map((download) => (
          <Button key={download.format} onClick={() => void save(download.format)} disabled={!ready || pending !== null}
            loading={pending === download.format} className="inline-flex items-center gap-2">
            <Download size={16} aria-hidden="true" />
            {download.label}
          </Button>
        ))}
      </div>
      {!ready ? <p className="text-[13px] leading-[18px] text-muted">Complete the choices above to preview or download the report.</p> : null}
      {error ? <ErrorBox error={error} /> : null}
    </div>
  )
}

function Preview({ entry, period }: { entry: ReportEntry; period: ReportParams }) {
  const report = useReport(entry.code, period)
  return (
    <Panel>
      {report.error ? <ErrorBox error={report.error} /> : null}
      {report.isPending ? <p className="text-sm text-muted">Preparing the report…</p> : null}
      {report.data ? <ReportPreview report={report.data} signedPercent={entry.signedPercent ?? []} /> : null}
    </Panel>
  )
}
