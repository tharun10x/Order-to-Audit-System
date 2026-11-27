import type { UseMutationResult } from '@tanstack/react-query'

import { Button } from '../../components/Button'
import { ErrorBox } from '../../components/Messages'

/**
 * R5.4: masters are deactivated, never deleted, so documents that name them stay readable. An inactive record is
 * left out of new orders, purchases and runs; reactivating brings it back. The backend may refuse (an item in an
 * active recipe, M10), and its sentence is shown here.
 */
export function ActiveToggle<T>({ what, active, mutation, onDone }: {
  /** "item", "customer", "supplier". */
  what: string
  active: boolean
  mutation: UseMutationResult<T, Error, boolean>
  onDone: (message: string) => void
}) {
  return (
    <div className="flex flex-col gap-3">
      <p className="text-sm text-muted">
        {active
          ? `Deactivating keeps every document that names this ${what}, but it can no longer be picked for new ones.`
          : `This ${what} is inactive: it cannot be picked for new documents until it is reactivated.`}
      </p>
      {mutation.error ? <ErrorBox error={mutation.error} /> : null}
      <div>
        <Button
          variant={active ? 'danger' : 'secondary'}
          loading={mutation.isPending}
          onClick={() => mutation.mutate(!active, {
            onSuccess: () => onDone(active ? `Deactivated. It no longer appears on new documents.` : 'Reactivated.'),
          })}
        >
          {active ? `Deactivate ${what}` : `Reactivate ${what}`}
        </Button>
      </div>
    </div>
  )
}
