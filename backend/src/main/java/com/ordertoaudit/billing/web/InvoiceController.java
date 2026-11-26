package com.ordertoaudit.billing.web;

import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ordertoaudit.billing.service.InvoicePdf;
import com.ordertoaudit.billing.service.InvoicePdfService;
import com.ordertoaudit.billing.service.InvoiceService;
import com.ordertoaudit.security.CurrentUserProvider;
import com.ordertoaudit.security.Roles;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final InvoicePdfService pdfService;
    private final CurrentUserProvider currentUser;

    public InvoiceController(InvoiceService invoiceService, InvoicePdfService pdfService,
                             CurrentUserProvider currentUser) {
        this.invoiceService = invoiceService;
        this.pdfService = pdfService;
        this.currentUser = currentUser;
    }

    @PreAuthorize(Roles.ADMIN_ONLY)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceResponse create(@Valid @RequestBody CreateInvoiceRequest request) {
        return invoiceService.createFromOrder(request.orderId(), request.invoiceDate(), currentUser.current());
    }

    @PreAuthorize(Roles.ACCOUNTS_READ)
    @GetMapping
    public List<InvoiceResponse> list() {
        return invoiceService.list(currentUser.current());
    }

    @PreAuthorize(Roles.ACCOUNTS_READ)
    @GetMapping("/{id}")
    public InvoiceResponse get(@PathVariable Long id) {
        return invoiceService.get(id, currentUser.current());
    }

    @PreAuthorize(Roles.ACCOUNTS_READ)
    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        InvoicePdf pdf = pdfService.pdf(id, currentUser.current());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(pdf.fileName()).build().toString())
                .body(pdf.content());
    }

    @PreAuthorize(Roles.ADMIN_ONLY)
    @PostMapping("/{id}/cancel")
    public InvoiceResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelInvoiceRequest request) {
        return invoiceService.cancel(id, request.reason(), request.noteDate(), currentUser.current());
    }
}
