import type { OrderStatus } from '../api/types'
import { STATUS_LOOK } from './status'

export function StatusText({ status }: { status: OrderStatus }) {
  return <span className={`font-medium ${STATUS_LOOK[status].className}`}>{STATUS_LOOK[status].label}</span>
}
