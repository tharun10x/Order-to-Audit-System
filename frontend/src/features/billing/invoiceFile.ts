import { download, type DownloadedFile } from '../../api/client'
import type { Invoice } from '../../api/types'
import { formatMoney } from '../../lib/format'

/** The invoice PDF, named as the server names it (Content-Disposition). */
export function fetchInvoicePdf(invoice: Invoice): Promise<DownloadedFile> {
  return download(`/api/invoices/${invoice.id}/pdf`, `${invoice.invoiceNo.replaceAll('/', '-')}.pdf`)
}

/** The message that goes with a shared invoice. */
export function shareText(invoice: Invoice): string {
  return `Invoice ${invoice.invoiceNo} for ${invoice.buyer.name}: ${formatMoney(invoice.total, { withSymbol: true })}.`
}

/** A WhatsApp link with the message filled in (brief 4.2: a WhatsApp share link is enough). */
export function whatsAppLink(invoice: Invoice): string {
  return `https://wa.me/?text=${encodeURIComponent(shareText(invoice))}`
}

/**
 * On a phone that can share files, opens the share sheet with the PDF attached and returns true.
 * Elsewhere returns false, and the caller downloads the PDF and offers the WhatsApp link.
 */
export async function shareFile(file: DownloadedFile, invoice: Invoice): Promise<boolean> {
  if (typeof navigator.canShare !== 'function') {
    return false
  }
  const pdf = new File([file.blob], file.fileName, { type: 'application/pdf' })
  if (!navigator.canShare({ files: [pdf] })) {
    return false
  }
  try {
    await navigator.share({ files: [pdf], title: invoice.invoiceNo, text: shareText(invoice) })
  } catch (error) {
    // Closing the share sheet is not an error
    if (!(error instanceof DOMException && error.name === 'AbortError')) {
      throw error
    }
  }
  return true
}
