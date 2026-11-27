import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { api } from './client'
import type { CreateOrderRequest, Customer, InvoiceSummary, Order, OrderStatus, Product, StockCheck } from './types'

/** Every query key in one place, so invalidation after an action cannot miss one. */
export const keys = {
  customers: ['customers'] as const,
  products: ['products'] as const,
  orders: (status?: OrderStatus) => ['orders', status ?? 'ALL'] as const,
  allOrders: ['orders'] as const,
  order: (id: number) => ['order', id] as const,
  stockCheck: (id: number) => ['stock-check', id] as const,
}

export function useCustomers() {
  return useQuery({ queryKey: keys.customers, queryFn: () => api.get<Customer[]>('/api/customers') })
}

export function useProducts() {
  return useQuery({ queryKey: keys.products, queryFn: () => api.get<Product[]>('/api/products') })
}

export function useOrders(status?: OrderStatus) {
  const path = status ? `/api/orders?status=${status}` : '/api/orders'
  return useQuery({ queryKey: keys.orders(status), queryFn: () => api.get<Order[]>(path) })
}

export function useOrder(id: number) {
  return useQuery({ queryKey: keys.order(id), queryFn: () => api.get<Order>(`/api/orders/${id}`) })
}

export function useStockCheck(id: number, enabled: boolean) {
  return useQuery({
    queryKey: keys.stockCheck(id),
    queryFn: () => api.get<StockCheck>(`/api/orders/${id}/stock-check`),
    enabled,
  })
}

/** After any change to an order: its detail, its stock check, and every list (stock reservations change too). */
function useRefreshOrders() {
  const queryClient = useQueryClient()
  return () => {
    void queryClient.invalidateQueries({ queryKey: keys.allOrders })
    void queryClient.invalidateQueries({ queryKey: ['order'] })
    void queryClient.invalidateQueries({ queryKey: ['stock-check'] })
  }
}

export function useCreateOrder() {
  const refresh = useRefreshOrders()
  return useMutation({
    mutationFn: (order: CreateOrderRequest) => api.post<Order>('/api/orders', order),
    onSuccess: refresh,
  })
}

export function useApproveOrder(id: number) {
  const refresh = useRefreshOrders()
  return useMutation({
    mutationFn: (lines: { itemCode: string; approvedQty: number }[] | null) =>
      api.post<Order>(`/api/orders/${id}/approve`, lines ? { lines } : undefined),
    onSuccess: refresh,
  })
}

export function useRejectOrder(id: number) {
  const refresh = useRefreshOrders()
  return useMutation({
    mutationFn: (reason: string) => api.post<Order>(`/api/orders/${id}/reject`, { reason }),
    onSuccess: refresh,
  })
}

export function useUpdateOrderLines(id: number) {
  const refresh = useRefreshOrders()
  return useMutation({
    mutationFn: (lines: { itemCode: string; qty: number; rate: number }[]) =>
      api.put<Order>(`/api/orders/${id}/lines`, { lines }),
    onSuccess: refresh,
  })
}

export function useCreateInvoice() {
  const refresh = useRefreshOrders()
  return useMutation({
    mutationFn: (request: { orderId: number; invoiceDate: string }) => api.post<InvoiceSummary>('/api/invoices', request),
    onSuccess: refresh,
  })
}
