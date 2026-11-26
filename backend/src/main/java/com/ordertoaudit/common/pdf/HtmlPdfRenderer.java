package com.ordertoaudit.common.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.openhtmltopdf.extend.FSSupplier;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

/**
 * Fills a Thymeleaf template and draws it as a PDF with openhtmltopdf. Used for invoices (R10.5) and reports
 * (R15). The built-in PDF fonts have no rupee sign, so DejaVu Sans is embedded; templates use font-family
 * 'DejaVu Sans'.
 */
@Component
public class HtmlPdfRenderer {

    private static final String FONT_FAMILY = "DejaVu Sans";
    private static final int NORMAL_WEIGHT = 400;
    private static final int BOLD_WEIGHT = 700;

    private final TemplateEngine templateEngine;

    public HtmlPdfRenderer(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    /** template is a path under templates/ without ".html", for example "invoice/tax-invoice". */
    public byte[] render(String template, Map<String, Object> model) {
        String html = templateEngine.process(template, new Context(null, model));
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFont(font("fonts/DejaVuSans.ttf"), FONT_FAMILY, NORMAL_WEIGHT, FontStyle.NORMAL, true);
            builder.useFont(font("fonts/DejaVuSans-Bold.ttf"), FONT_FAMILY, BOLD_WEIGHT, FontStyle.NORMAL, true);
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create the PDF from " + template, e);
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
