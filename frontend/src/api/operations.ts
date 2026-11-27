import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { api } from './client'
import type {
  ActualsRequest, Balance, Item, LedgerRow, OverheadType, Purchase, PurchaseRequest, Run, RunSummary, Supplier,
} from './types'

/** Purchases, production runs and stock (R6 to R8, R13). */
export const operationsKeys = {
  items: ['items'] as const,
  suppliers: ['suppliers'] as const,
  purchases: ['purchases'] as const,
  runs: ['runs'] as const,
  run: (id: number) => ['runs', id] as const,
  balances: ['stock', 'balances'] as const,
  ledger: (itemCode: string, from: string, to: string) => ['stock', 'ledger', itemCode, from, to] as const,
}

export function useItems() {
  return useQuery({ queryKey: operationsKeys.items, queryFn: () => api.get<Item[]>('/api/items') })
}

export function useSuppliers() {
  return useQuery({ queryKey: operationsKeys.suppliers, queryFn: () => api.get<Supplier[]>('/api/suppliers') })
}

export function usePurchases() {
  return useQuery({ queryKey: operationsKeys.purchases, queryFn: () => api.get<Purchase[]>('/api/purchases') })
}

export function useRuns() {
  return useQuery({ queryKey: operationsKeys.runs, queryFn: () => api.get<RunSummary[]>('/api/production-runs') })
}

export function useRun(id: number) {
  return useQuery({ queryKey: operationsKeys.run(id), queryFn: () => api.get<Run>(`/api/production-runs/${id}`) })
}

export function useBalances() {
  return useQuery({ queryKey: operationsKeys.balances, queryFn: () => api.get<Balance[]>('/api/inventory/balances') })
}

export function useLedger(itemCode: string, from: string, to: string) {
  const query = new URLSearchParams({ itemCode, from, to }).toString()
  return useQuery({
    queryKey: operationsKeys.ledger(itemCode, from, to),
    queryFn: () => api.get<LedgerRow[]>(`/api/inventory/ledger?${query}`),
    enabled: itemCode !== '',
  })
}

/** Stock moved: balances, ledgers, runs and purchases are read again. */
function useRefreshOperations() {
  const queryClient = useQueryClient()
  return () => {
    for (const key of [['stock'], ['runs'], ['purchases'], ['stock-check']]) {
      void queryClient.invalidateQueries({ queryKey: key })
    }
  }
}

export function useRecordPurchase() {
  const refresh = useRefreshOperations()
  return useMutation({
    mutationFn: (request: PurchaseRequest) => api.post<Purchase>('/api/purchases', request),
    onSuccess: refresh,
  })
}

export function useCreateRun() {
  const refresh = useRefreshOperations()
  return useMutation({
    mutationFn: (request: { productCode: string; runDate: string; batches: string }) =>
      api.post<Run>('/api/production-runs', request),
    onSuccess: refresh,
  })
}

export function useSaveActuals(runId: number) {
  const refresh = useRefreshOperations()
  return useMutation({
    mutationFn: (request: ActualsRequest) => api.put<Run>(`/api/production-runs/${runId}/actuals`, request),
    onSuccess: refresh,
  })
}

export function usePostRun(runId: number) {
  const refresh = useRefreshOperations()
  return useMutation({ mutationFn: () => api.post<Run>(`/api/production-runs/${runId}/post`), onSuccess: refresh })
}

export function useCancelRun(runId: number) {
  const refresh = useRefreshOperations()
  return useMutation({ mutationFn: () => api.post<Run>(`/api/production-runs/${runId}/cancel`), onSuccess: refresh })
}

export function useAddOverhead(runId: number) {
  const refresh = useRefreshOperations()
  return useMutation({
    mutationFn: (request: { type: OverheadType; amount: string; note: string | null }) =>
      api.post<Run>(`/api/production-runs/${runId}/overheads`, request),
    onSuccess: refresh,
  })
}
