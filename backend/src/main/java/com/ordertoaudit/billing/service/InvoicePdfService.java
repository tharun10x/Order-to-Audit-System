package com.ordertoaudit.billing.service;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ordertoaudit.billing.entity.CreditNote;
import com.ordertoaudit.billing.entity.Invoice;
import com.ordertoaudit.billing.repository.CreditNoteRepository;
import com.ordertoaudit.billing.repository.InvoiceRepository;
import com.ordertoaudit.common.error.NotFoundException;
import com.ordertoaudit.common.pdf.HtmlPdfRenderer;
import com.ordertoaudit.masters.entity.AppUser;

/** The GST tax invoice as a PDF (R10.5), from the invoice's own copied details only. */
@Service
public class InvoicePdfService {

    private static final String TEMPLATE = "invoice/tax-invoice";

    private final InvoiceRepository invoiceRepository;
    private final CreditNoteRepository creditNoteRepository;
    private final HtmlPdfRenderer pdfRenderer;

    public InvoicePdfService(InvoiceRepository invoiceRepository, CreditNoteRepository creditNoteRepository,
                             HtmlPdfRenderer pdfRenderer) {
        this.invoiceRepository = invoiceRepository;
        this.creditNoteRepository = creditNoteRepository;
        this.pdfRenderer = pdfRenderer;
    }

    @Transactional(readOnly = true)
    public InvoicePdf pdf(Long invoiceId, AppUser user) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .filter(found -> found.isVisibleTo(user))
                .orElseThrow(() -> new NotFoundException("Invoice " + invoiceId + " not found"));
        String creditNoteNo = creditNoteRepository.findByInvoiceId(invoiceId).map(CreditNote::getNoteNo).orElse(null);

        InvoicePrintView view = InvoicePrintView.of(invoice, creditNoteNo);
        byte[] content = pdfRenderer.render(TEMPLATE, Map.of("invoice", view));
        return new InvoicePdf(invoice.getInvoiceNo().replace('/', '-') + ".pdf", content);
    }
}
