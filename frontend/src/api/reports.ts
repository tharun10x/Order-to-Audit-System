import { useQuery } from '@tanstack/react-query'

import { api, download, type DownloadedFile } from './client'
import type { Dashboard, Report } from './types'

/** R15: one report for a period (both dates inclusive); itemCode only for the stock ledger. */
export interface ReportParams {
  from: string
  to: string
  itemCode?: string
}

export type DownloadFormat = 'csv' | 'xlsx' | 'pdf'

function reportPath(code: string, params: ReportParams, format: string): string {
  const query = new URLSearchParams({ from: params.from, to: params.to })
  if (params.itemCode) {
    query.set('itemCode', params.itemCode)
  }
  query.set('format', format)
  return `/api/reports/${code}?${query.toString()}`
}

/** The preview: the report as JSON. Not asked for until enabled (the inputs are complete). */
export function useReport(code: string, params: ReportParams, enabled = true) {
  return useQuery({
    queryKey: ['reports', code, params.from, params.to, params.itemCode ?? ''],
    queryFn: () => api.get<Report>(reportPath(code, params, 'json')),
    enabled,
  })
}

/** The same report as a file, named as the server names it (Content-Disposition, R15.1). */
export function downloadReport(code: string, params: ReportParams, format: DownloadFormat): Promise<DownloadedFile> {
  const subject = params.itemCode ? `_${params.itemCode}` : ''
  return download(reportPath(code, params, format), `${code}${subject}_${params.from}_${params.to}.${format}`)
}

export function useDashboard() {
  return useQuery({ queryKey: ['dashboard'], queryFn: () => api.get<Dashboard>('/api/dashboard') })
}
