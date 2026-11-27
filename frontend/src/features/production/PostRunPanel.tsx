import { useState } from 'react'

import { useCancelRun, usePostRun } from '../../api/operations'
import type { Run } from '../../api/types'
import { Button } from '../../components/Button'
import { ErrorBox } from '../../components/Messages'
import { Panel } from '../../components/Panel'
import { formatQty } from '../../lib/format'

/**
 * R8.2: posting is the one moment a run moves stock, and it happens once. The confirmation lists exactly what will
 * leave stock (every item issued, scrap included) and what will enter (the good output). If stock is short the
 * backend refuses with one line per short item, shown as sent, and the run stays a draft.
 */
export function PostRunPanel({ run, ready, onPosted, onCancelled }: {
  run: Run
  ready: boolean
  onPosted: (run: Run) => void
  onCancelled: () => void
}) {
  const post = usePostRun(run.id)
  const cancel = useCancelRun(run.id)
  const [confirming, setConfirming] = useState(false)
  const issued = run.lines.filter((line) => (line.actualQty ?? 0) > 0)

  return (
    <Panel title="Post run">
      {!ready ? (
        <p className="mb-3 text-sm text-muted">Save the actuals and the good output first; then the run can be posted.</p>
      ) : null}
      {confirming ? (
        <div role="alertdialog" aria-labelledby="post-what" className="mb-4 rounded-control border border-border p-4 text-sm">
          <p id="post-what" className="font-semibold">{`Post run ${run.runNo}? Stock moves now, and the run cannot be edited afterwards.`}</p>
          <div className="mt-3 grid gap-4 sm:grid-cols-2">
            <div>
              <p className="text-muted">Leaves stock</p>
              <ul aria-label="Leaves stock" className="num mt-1">
                {issued.map((line) => (
                  <li key={line.itemCode}>{`${line.itemName}: ${formatQty(line.actualQty!, line.unit)}`}</li>
                ))}
              </ul>
            </div>
            <div>
              <p className="text-muted">Enters stock</p>
              <ul aria-label="Enters stock" className="num mt-1">
                <li>{`${run.productName}: ${formatQty(run.actualOutput ?? 0, 'pkt')}`}</li>
              </ul>
            </div>
          </div>
          <div className="mt-4 flex flex-wrap gap-3">
            <Button variant="primary" loading={post.isPending}
              onClick={() => post.mutate(undefined, { onSuccess: onPosted, onSettled: () => setConfirming(false) })}>
              {`Yes, post ${run.runNo}`}
            </Button>
            <Button onClick={() => setConfirming(false)} disabled={post.isPending}>Not yet</Button>
          </div>
        </div>
      ) : null}
      <div className="flex flex-col gap-3">
        {post.error ? <ErrorBox error={post.error} /> : null}
        {cancel.error ? <ErrorBox error={cancel.error} /> : null}
        {!confirming ? (
          <div className="flex flex-wrap gap-3">
            <Button variant="primary" disabled={!ready} onClick={() => setConfirming(true)}>Post run</Button>
            <Button variant="danger" loading={cancel.isPending} onClick={() => cancel.mutate(undefined, { onSuccess: onCancelled })}>
              Cancel run
            </Button>
          </div>
        ) : null}
      </div>
    </Panel>
  )
}
