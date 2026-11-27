import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'

import { api } from './client'
import type {
  Bom, BomRequest, Customer, CustomerRequest, Item, ItemRequest, MasterStatus, Salesman, Supplier, SupplierRequest,
} from './types'

/**
 * The admin's master data (R5): items, recipes, customers, suppliers. The keys start with the same word as the
 * keys of the order, purchase and production screens (['items'], ['customers'], ['suppliers']), so one
 * invalidation refreshes the masters lists and every picker that shows the same records.
 */
export const mastersKeys = {
  items: (status: MasterStatus) => ['items', status] as const,
  customers: (status: MasterStatus) => ['customers', status] as const,
  suppliers: (status: MasterStatus) => ['suppliers', status] as const,
  salesmen: ['salesmen'] as const,
  recipes: (productCode: string) => ['boms', productCode] as const,
}

export function useMasterItems(status: MasterStatus) {
  return useQuery({ queryKey: mastersKeys.items(status), queryFn: () => api.get<Item[]>(`/api/items?status=${status}`) })
}

export function useMasterCustomers(status: MasterStatus) {
  return useQuery({
    queryKey: mastersKeys.customers(status),
    queryFn: () => api.get<Customer[]>(`/api/customers?status=${status}`),
  })
}

export function useMasterSuppliers(status: MasterStatus, enabled = true) {
  return useQuery({
    queryKey: mastersKeys.suppliers(status),
    queryFn: () => api.get<Supplier[]>(`/api/suppliers?status=${status}`),
    enabled,
  })
}

/** Admin only (M5). */
export function useSalesmen() {
  return useQuery({ queryKey: mastersKeys.salesmen, queryFn: () => api.get<Salesman[]>('/api/salesmen') })
}

/** Every version of one product's recipe, newest first. Also used to fetch a recipe to copy from. */
export function recipesQuery(productCode: string) {
  return {
    queryKey: mastersKeys.recipes(productCode),
    queryFn: () => api.get<Bom[]>(`/api/boms?productCode=${encodeURIComponent(productCode)}`),
  }
}

export function useRecipes(productCode: string) {
  return useQuery({ ...recipesQuery(productCode), enabled: productCode !== '' })
}

/** Refreshes every query whose key starts with one of these words. */
function useRefresh(...words: string[]) {
  const queryClient = useQueryClient()
  return () => {
    for (const word of words) {
      void queryClient.invalidateQueries({ queryKey: [word] })
    }
  }
}

/**
 * Puts a saved record into the cached "all" list straight away. The page opens a record by looking it up there,
 * so a record just created is found before the list has been read again.
 */
function remember<T>(queryClient: QueryClient, key: readonly unknown[], saved: T, same: (a: T, b: T) => boolean) {
  queryClient.setQueryData<T[]>(key, (list) =>
    list === undefined ? undefined : list.some((record) => same(record, saved))
      ? list.map((record) => (same(record, saved) ? saved : record))
      : [...list, saved])
}

const sameItem = (a: Item, b: Item) => a.code === b.code
const sameId = (a: { id: number }, b: { id: number }) => a.id === b.id

// An item's lock reason, hasActiveBom and units change with recipes, and products appear on the order screen
const ITEM_QUERIES = ['items', 'products', 'boms']

export function useSaveItem(existingCode: string | null) {
  const queryClient = useQueryClient()
  const refresh = useRefresh(...ITEM_QUERIES)
  return useMutation({
    mutationFn: (request: ItemRequest) =>
      existingCode ? api.put<Item>(`/api/items/${encodeURIComponent(existingCode)}`, request) : api.post<Item>('/api/items', request),
    onSuccess: (saved) => {
      remember(queryClient, mastersKeys.items('all'), saved, sameItem)
      refresh()
    },
  })
}

export function useSetItemActive(code: string) {
  const queryClient = useQueryClient()
  const refresh = useRefresh(...ITEM_QUERIES)
  return useMutation({
    mutationFn: (active: boolean) =>
      api.post<Item>(`/api/items/${encodeURIComponent(code)}/${active ? 'reactivate' : 'deactivate'}`),
    onSuccess: (saved) => {
      remember(queryClient, mastersKeys.items('all'), saved, sameItem)
      refresh()
    },
  })
}

export function useSaveCustomer(existingId: number | null) {
  const queryClient = useQueryClient()
  const refresh = useRefresh('customers')
  return useMutation({
    mutationFn: (request: CustomerRequest) =>
      existingId ? api.put<Customer>(`/api/customers/${existingId}`, request) : api.post<Customer>('/api/customers', request),
    onSuccess: (saved) => {
      remember(queryClient, mastersKeys.customers('all'), saved, sameId)
      refresh()
    },
  })
}

export function useSetCustomerActive(id: number) {
  const queryClient = useQueryClient()
  const refresh = useRefresh('customers')
  return useMutation({
    mutationFn: (active: boolean) => api.post<Customer>(`/api/customers/${id}/${active ? 'reactivate' : 'deactivate'}`),
    onSuccess: (saved) => {
      remember(queryClient, mastersKeys.customers('all'), saved, sameId)
      refresh()
    },
  })
}

export function useSaveSupplier(existingId: number | null) {
  const queryClient = useQueryClient()
  const refresh = useRefresh('suppliers')
  return useMutation({
    mutationFn: (request: SupplierRequest) =>
      existingId ? api.put<Supplier>(`/api/suppliers/${existingId}`, request) : api.post<Supplier>('/api/suppliers', request),
    onSuccess: (saved) => {
      remember(queryClient, mastersKeys.suppliers('all'), saved, sameId)
      refresh()
    },
  })
}

export function useSetSupplierActive(id: number) {
  const queryClient = useQueryClient()
  const refresh = useRefresh('suppliers')
  return useMutation({
    mutationFn: (active: boolean) => api.post<Supplier>(`/api/suppliers/${id}/${active ? 'reactivate' : 'deactivate'}`),
    onSuccess: (saved) => {
      remember(queryClient, mastersKeys.suppliers('all'), saved, sameId)
      refresh()
    },
  })
}

export function useCreateRecipe() {
  const refresh = useRefresh(...ITEM_QUERIES)
  return useMutation({ mutationFn: (request: BomRequest) => api.post<Bom>('/api/boms', request), onSuccess: refresh })
}

export function useActivateRecipe() {
  const refresh = useRefresh(...ITEM_QUERIES)
  return useMutation({ mutationFn: (id: number) => api.post<Bom>(`/api/boms/${id}/activate`), onSuccess: refresh })
}
