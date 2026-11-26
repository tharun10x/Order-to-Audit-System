package com.ordertoaudit.billing.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.openhtmltopdf.extend.FSSupplier;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.ordertoaudit.billing.entity.CreditNote;
import com.ordertoaudit.billing.entity.Invoice;
import com.ordertoaudit.billing.repository.CreditNoteRepository;
import com.ordertoaudit.billing.repository.InvoiceRepository;
import com.ordertoaudit.common.error.NotFoundException;
import com.ordertoaudit.masters.entity.AppUser;

/** The GST tax invoice as a PDF (R10.5): Thymeleaf fills the HTML template and openhtmltopdf draws it. */
@Service
public class InvoicePdfService {

    private static final String TEMPLATE = "invoice/tax-invoice";
    /** The built-in PDF fonts have no rupee sign, so DejaVu Sans is embedded. */
    private static final String FONT_FAMILY = "DejaVu Sans";
    private static final int NORMAL_WEIGHT = 400;
    private static final int BOLD_WEIGHT = 700;

    private final InvoiceRepository invoiceRepository;
    private final CreditNoteRepository creditNoteRepository;
    private final TemplateEngine templateEngine;

    public InvoicePdfService(InvoiceRepository invoiceRepository, CreditNoteRepository creditNoteRepository,
                             TemplateEngine templateEngine) {
        this.invoiceRepository = invoiceRepository;
        this.creditNoteRepository = creditNoteRepository;
        this.templateEngine = templateEngine;
    }

    @Transactional(readOnly = true)
    public InvoicePdf pdf(Long invoiceId, AppUser user) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .filter(found -> found.isVisibleTo(user))
                .orElseThrow(() -> new NotFoundException("Invoice " + invoiceId + " not found"));
        String creditNoteNo = creditNoteRepository.findByInvoiceId(invoiceId).map(CreditNote::getNoteNo).orElse(null);

        InvoicePrintView view = InvoicePrintView.of(invoice, creditNoteNo);
        String html = templateEngine.process(TEMPLATE, new Context(null, Map.of("invoice", view)));
        return new InvoicePdf(invoice.getInvoiceNo().replace('/', '-') + ".pdf", render(html));
    }

    private byte[] render(String html) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFont(font("fonts/DejaVuSans.ttf"), FONT_FAMILY, NORMAL_WEIGHT, FontStyle.NORMAL, true);
            builder.useFont(font("fonts/DejaVuSans-Bold.ttf"), FONT_FAMILY, BOLD_WEIGHT, FontStyle.NORMAL, true);
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create the invoice PDF", e);
        }
    }

    private FSSupplier<InputStream> font(String path) {
        return () -> {
            try {
                return new ClassPathResource(path).getInputStream();
            } catch (IOException e) {
                throw new UncheckedIOException("Font missing from the application: " + path, e);
            }
        };
    }
}
