import type { Unit } from '../../api/types'
import { newClientUuid } from '../../lib/clientUuid'

/**
 * The order being written on the phone, kept in sessionStorage so a reload or a dropped connection loses
 * nothing. clientUuid names exactly this content (R9.7): sending it again after a network error cannot create a
 * second order. Any change to the content gets a new UUID, because it is then a different order.
 */
export interface DraftQuantity {
  qty: number
  unit: Unit
}

export interface OrderDraft {
  customerId: number | null
  /** Per product code. */
  quantities: Record<string, DraftQuantity>
  clientUuid: string
}

const KEY = 'ota.orderDraft'

export function newDraft(): OrderDraft {
  return { customerId: null, quantities: {}, clientUuid: newClientUuid() }
}

export function loadDraft(): OrderDraft {
  const stored = sessionStorage.getItem(KEY)
  if (stored) {
    try {
      return JSON.parse(stored) as OrderDraft
    } catch {
      sessionStorage.removeItem(KEY)
    }
  }
  return newDraft()
}

export function saveDraft(draft: OrderDraft): void {
  sessionStorage.setItem(KEY, JSON.stringify(draft))
}

/** The same draft with some content changed, under a new UUID. */
export function changeDraft(draft: OrderDraft, change: Partial<Omit<OrderDraft, 'clientUuid'>>): OrderDraft {
  return { ...draft, ...change, clientUuid: newClientUuid() }
}
