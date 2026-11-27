import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { api } from './client'
import type { CustomerOutstanding, Dispatch, Invoice, InvoiceStatus, Payment, PaymentMode } from './types'

/** Invoices, dispatch and payments (R10 to R12). */
export const billingKeys = {
  invoices: ['invoices'] as const,
  invoiceList: (filter: InvoiceFilter) => ['invoices', 'list', filter] as const,
  invoice: (id: number) => ['invoices', id] as const,
  payments: ['payments'] as const,
  outstanding: (customerId: number) => ['outstanding', customerId] as const,
}

export interface InvoiceFilter {
  status?: InvoiceStatus
  from?: string
  to?: string
}

export function useInvoices(filter: InvoiceFilter) {
  const params = new URLSearchParams()
  if (filter.status) params.set('status', filter.status)
  if (filter.from) params.set('from', filter.from)
  if (filter.to) params.set('to', filter.to)
  const query = params.toString()
  return useQuery({
    queryKey: billingKeys.invoiceList(filter),
    queryFn: () => api.get<Invoice[]>(`/api/invoices${query ? `?${query}` : ''}`),
  })
}

export function useInvoice(id: number) {
  return useQuery({ queryKey: billingKeys.invoice(id), queryFn: () => api.get<Invoice>(`/api/invoices/${id}`) })
}

export function usePayments() {
  return useQuery({ queryKey: billingKeys.payments, queryFn: () => api.get<Payment[]>('/api/payments') })
}

export function useCustomerOutstanding(customerId: number | null) {
  return useQuery({
    queryKey: billingKeys.outstanding(customerId ?? 0),
    queryFn: () => api.get<CustomerOutstanding>(`/api/customers/${customerId}/outstanding`),
    enabled: customerId !== null,
  })
}

/**
 * After a dispatch, a cancellation or a payment, everything that shows its effect is read again: the invoice,
 * the lists, the order's status, outstanding and stock.
 */
function useRefreshAfterBilling() {
  const queryClient = useQueryClient()
  return () => {
    for (const key of [['invoices'], ['payments'], ['outstanding'], ['orders'], ['order'], ['stock-check'], ['stock']]) {
      void queryClient.invalidateQueries({ queryKey: key })
    }
  }
}

export function useDispatch() {
  const refresh = useRefreshAfterBilling()
  return useMutation({
    mutationFn: (request: { invoiceId: number; dispatchDate: string; vehicleNo: string | null }) =>
      api.post<Dispatch>('/api/dispatches', request),
    onSuccess: refresh,
  })
}

export function useCancelInvoice(invoiceId: number) {
  const refresh = useRefreshAfterBilling()
  return useMutation({
    mutationFn: (reason: string) => api.post<Invoice>(`/api/invoices/${invoiceId}/cancel`, { reason }),
    onSuccess: refresh,
  })
}

export interface PaymentRequest {
  customerId: number
  receivedOn: string
  /** Sent as typed (at most 2 decimals); the backend checks and rounds nothing. */
  amount: string
  mode: PaymentMode
  reference: string | null
  allocations: { invoiceId: number; amount: string }[]
}

export function useRecordPayment() {
  const refresh = useRefreshAfterBilling()
  return useMutation({
    mutationFn: (request: PaymentRequest) => api.post<Payment>('/api/payments', request),
    onSuccess: refresh,
  })
}
