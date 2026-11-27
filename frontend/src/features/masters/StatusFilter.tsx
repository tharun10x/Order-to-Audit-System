import { useSearchParams } from 'react-router'

import { Field } from '../../components/Field'
import { Select } from '../../components/Layout'
import { STATUSES, useMasterStatus } from './masterStatus'

export function StatusFilter() {
  const [params, setParams] = useSearchParams()
  const status = useMasterStatus()
  return (
    <div className="w-44">
      <Field label="Show">
        {({ id }) => (
          <Select
            id={id}
            value={status}
            onChange={(event) => {
              const next = new URLSearchParams(params)
              if (event.target.value === 'active') {
                next.delete('status')
              } else {
                next.set('status', event.target.value)
              }
              setParams(next)
            }}
          >
            {STATUSES.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
          </Select>
        )}
      </Field>
    </div>
  )
}

/** Active in ink, Inactive in muted: a word, never colour alone (UI guide 6). */
export function ActiveWord({ active }: { active: boolean }) {
  return active ? <span className="font-medium text-ink">Active</span> : <span className="text-muted">Inactive</span>
}
