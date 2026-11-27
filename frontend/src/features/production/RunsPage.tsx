import { Link } from 'react-router'

import { useItems, useRuns } from '../../api/operations'
import { useSession } from '../../auth/authContext'
import { can } from '../../auth/permissions'
import { LinkButton, PageHeader, StatusWord } from '../../components/Layout'
import { EmptyState, ErrorBox } from '../../components/Messages'
import { Panel } from '../../components/Panel'
import { Table, Td, Th } from '../../components/Table'
import { formatDate, formatMoney, formatNumber } from '../../lib/format'
import { RUN_STATUS } from '../../lib/labels'
import { Variance } from './Variance'

/** R8: production runs with their yield and cost, all worked out by the backend's run report. */
export function RunsPage() {
  const session = useSession()
  const runs = useRuns()
  const items = useItems()
  const productName = (code: string) => items.data?.find((item) => item.code === code)?.name ?? code

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Production runs"
        action={can.runProduction(session.user.role) ? <LinkButton variant="primary" to="/production/runs/new">New run</LinkButton> : undefined}
      />
      <Panel>
        {runs.error ? <ErrorBox error={runs.error} /> : null}
        {runs.data && runs.data.length === 0 ? (
          <EmptyState>No runs yet. Production runs are started by the production supervisor.</EmptyState>
        ) : null}
        {runs.data && runs.data.length > 0 ? (
          <Table label="Production runs">
            <thead>
              <tr>
                <Th>Run no</Th>
                <Th>Date</Th>
                <Th>Product</Th>
                <Th>Status</Th>
                <Th numeric>Good output (pkt)</Th>
                <Th numeric>Yield variance</Th>
                <Th numeric>Cost per packet (₹)</Th>
              </tr>
            </thead>
            <tbody>
              {[...runs.data].reverse().map((run) => (
                <tr key={run.id}>
                  <Td className="whitespace-nowrap">
                    <Link to={`/production/runs/${run.id}`} className="font-medium text-primary hover:underline">{run.runNo}</Link>
                  </Td>
                  <Td className="whitespace-nowrap">{formatDate(run.runDate)}</Td>
                  <Td>{productName(run.productCode)}</Td>
                  <Td><StatusWord look={RUN_STATUS[run.status]} /></Td>
                  <Td numeric>{run.actualOutput === null ? '' : formatNumber(run.actualOutput)}</Td>
                  <Td numeric><Variance pct={run.yieldVariancePct} flagged={run.yieldFlagged} /></Td>
                  <Td numeric>{run.costPerPacket === null ? '' : formatMoney(run.costPerPacket)}</Td>
                </tr>
              ))}
            </tbody>
          </Table>
        ) : null}
      </Panel>
    </div>
  )
}
