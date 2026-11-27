import { useState } from 'react'
import { Link, useParams } from 'react-router'

import { useRun } from '../../api/operations'
import type { Run } from '../../api/types'
import { useSession } from '../../auth/authContext'
import { can } from '../../auth/permissions'
import { Facts, PageHeader, StatusWord } from '../../components/Layout'
import { ErrorBox, SuccessLine } from '../../components/Messages'
import { Panel } from '../../components/Panel'
import { Table, Td, Th } from '../../components/Table'
import { formatDate, formatMoney, formatNumber, formatPercent, formatQty, formatRate } from '../../lib/format'
import { OVERHEAD_TYPE, RUN_STATUS } from '../../lib/labels'
import { ActualsForm } from './ActualsForm'
import { OverheadsPanel } from './OverheadsPanel'
import { PostRunPanel } from './PostRunPanel'
import { Variance } from './Variance'

/**
 * One run. DRAFT: the production supervisor enters actuals and posts (R8.2). Once there are actuals: planned vs
 * actual with variances (R8.7 to R8.9). POSTED: the cost per packet (R13). Others only read.
 */
export function RunDetailPage() {
  const { runId } = useParams()
  const run = useRun(Number(runId))
  const session = useSession()
  const [notice, setNotice] = useState<string | null>(null)

  if (run.error) {
    return <ErrorBox error={run.error} />
  }
  if (!run.data) {
    return <p className="text-sm text-muted">Loading run…</p>
  }
  const data = run.data
  const operate = can.runProduction(session.user.role)
  const draft = data.status === 'DRAFT'
  const hasActuals = data.actualOutput !== null && data.lines.every((line) => line.actualQty !== null)

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title={`Run ${data.runNo}`}>
        <p className="mt-1 text-sm"><Link to="/production/runs" className="text-primary hover:underline">All runs</Link></p>
      </PageHeader>
      {notice ? <SuccessLine>{notice}</SuccessLine> : null}

      <Panel>
        <Facts
          items={[
            ['Product', data.productName],
            ['Run date', formatDate(data.runDate)],
            ['Status', <StatusWord key="s" look={RUN_STATUS[data.status]} />],
            ['Batches', formatNumber(data.batches)],
            ['Expected output', formatQty(data.plannedOutput, 'pkt')],
            ['Variance tolerance', `±${data.tolerancePct.toFixed(2)}%`],
            ...(data.postedBy ? [['Posted by', data.postedBy] as [string, string]] : []),
          ]}
        />
      </Panel>

      {draft && operate ? (
        <ActualsForm key={`${data.id}-${data.actualOutput}`} run={data} onSaved={() => setNotice('Actuals saved. The run is still a draft.')} />
      ) : null}

      {hasActuals || !draft ? <PlannedVsActual run={data} /> : <PlannedTable run={data} />}

      {draft && operate ? (
        <PostRunPanel run={data} ready={hasActuals} onPosted={(posted) => setNotice(`Run ${posted.runNo} posted. Stock is updated.`)}
          onCancelled={() => setNotice('Run cancelled. It moved no stock.')} />
      ) : null}

      {data.costing ? <CostBlock run={data} /> : null}

      {data.status === 'DRAFT' || data.status === 'POSTED' ? (
        <OverheadsPanel run={data} canAdd={operate} />
      ) : null}
    </div>
  )
}

/** A draft before actuals: what the recipe expects. */
function PlannedTable({ run }: { run: Run }) {
  return (
    <Panel title="Planned quantities">
      <Table label="Planned quantities">
        <thead>
          <tr>
            <Th>Item</Th>
            <Th numeric>Planned</Th>
          </tr>
        </thead>
        <tbody>
          {run.lines.map((line) => (
            <tr key={line.itemCode}>
              <Td>{line.itemName}</Td>
              <Td numeric>{formatQty(line.plannedQty, line.unit)}</Td>
            </tr>
          ))}
        </tbody>
      </Table>
      <p className="mt-2 text-[13px] leading-[18px] text-muted">
        Packaging standards are recalculated from the good output when the run is posted.
      </p>
    </Panel>
  )
}

/** R8.7 to R8.9: standard vs actual (used + scrap), flagged beyond the tolerance. */
function PlannedVsActual({ run }: { run: Run }) {
  return (
    <Panel title="Planned vs actual">
      <Table label="Planned vs actual">
        <thead>
          <tr>
            <Th>Item</Th>
            <Th numeric>Standard</Th>
            <Th numeric>Actual</Th>
            <Th numeric>Scrap / damaged</Th>
            <Th numeric>Variance</Th>
            {run.status === 'POSTED' ? <Th numeric>Value (₹)</Th> : null}
          </tr>
        </thead>
        <tbody>
          {run.lines.map((line) => (
            <tr key={line.itemCode}>
              <Td>{line.itemName}</Td>
              <Td numeric>{formatQty(line.plannedQty, line.unit)}</Td>
              <Td numeric>{line.actualQty === null ? '' : formatQty(line.actualQty, line.unit)}</Td>
              <Td numeric>{formatQty(line.scrapQty, line.unit)}</Td>
              <Td numeric>
                {run.status === 'DRAFT' && line.kind === 'PACKAGING'
                  ? <span className="text-muted">Set when posted</span>
                  : <Variance pct={line.variancePct} flagged={line.flagged} />}
              </Td>
              {run.status === 'POSTED' ? <Td numeric>{line.value === null ? '' : formatMoney(line.value)}</Td> : null}
            </tr>
          ))}
          <tr>
            <Td className="font-semibold">Good output</Td>
            <Td numeric>{formatQty(run.plannedOutput, 'pkt')}</Td>
            <Td numeric>{run.actualOutput === null ? '' : formatQty(run.actualOutput, 'pkt')}</Td>
            <Td />
            <Td numeric><Variance pct={run.yieldVariancePct} flagged={run.yieldFlagged} /></Td>
            {run.status === 'POSTED' ? <Td /> : null}
          </tr>
        </tbody>
      </Table>
      {run.status === 'DRAFT' ? (
        <p className="mt-2 text-[13px] leading-[18px] text-muted">
          Packaging is judged against what was packed (R8.6): its standard is recalculated from the good output when the
          run is posted, so its variance is shown then.
        </p>
      ) : null}
    </Panel>
  )
}

/** R13: material and packaging at average cost when issued, overheads entered on the run. All from the backend. */
function CostBlock({ run }: { run: Run }) {
  const costing = run.costing!
  const row = (label: string, amount: string, strong = false) => (
    <div key={label} className={`flex justify-between gap-6 py-1 ${strong ? 'border-t border-rule pt-2 font-semibold' : ''}`}>
      <dt>{label}</dt>
      <dd className="num">{amount}</dd>
    </div>
  )
  return (
    <Panel title="Cost per packet">
      <dl className="max-w-md text-sm">
        {row('Material', formatMoney(costing.materialCost, { withSymbol: true }))}
        {row('Packaging', formatMoney(costing.packagingCost, { withSymbol: true }))}
        {(Object.keys(OVERHEAD_TYPE) as (keyof typeof OVERHEAD_TYPE)[]).map((type) =>
          row(OVERHEAD_TYPE[type], formatMoney(costing.overheadsByType[type] ?? 0, { withSymbol: true })))}
        {row('Total run cost', formatMoney(costing.totalCost, { withSymbol: true }), true)}
        {row(`Cost per packet (${formatQty(run.actualOutput ?? 0, 'pkt')})`, formatMoney(costing.costPerPacketRounded, { withSymbol: true }), true)}
        {row('Selling price before GST', formatMoney(costing.sellingPrice, { withSymbol: true }))}
        {row('Margin per packet', formatMoney(costing.marginPerPacketRounded, { withSymbol: true }))}
        {row('Margin', costing.marginPct === null ? '' : formatPercent(costing.marginPct).replace(/^\+/, ''))}
      </dl>
      <p className="mt-2 text-[13px] leading-[18px] text-muted">
        {`Cost per packet to 4 decimals: ₹${formatRate(costing.costPerPacket)}. Overheads count here but not in stock value (R13.6).`}
      </p>
    </Panel>
  )
}
