import { useSearchParams } from 'react-router'

import type { MasterStatus } from '../../api/types'

export const STATUSES: { value: MasterStatus; label: string }[] = [
  { value: 'active', label: 'Active' },
  { value: 'inactive', label: 'Inactive' },
  { value: 'all', label: 'All' },
]

/** M6: which records a master list shows, kept in ?status= so a link or a reload keeps it. Active by default. */
export function useMasterStatus(): MasterStatus {
  const [params] = useSearchParams()
  const value = params.get('status')
  return STATUSES.some((status) => status.value === value) ? (value as MasterStatus) : 'active'
}

/** "?status=inactive" while a filter other than the default is chosen, so links between records keep it. */
export function useStatusQuery(): string {
  const status = useMasterStatus()
  return status === 'active' ? '' : `?status=${status}`
}
