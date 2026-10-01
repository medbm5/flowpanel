package com.flowpanel.invoice;

import com.flowpanel.invoice.InvoiceService.InvoicesView;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "invoice")
public class InvoiceController {

    public record ExtractRequest(String text) {
    }

    private final InvoiceService service;
    private final ClosingService closing;

    public InvoiceController(InvoiceService service, ClosingService closing) {
        this.service = service;
        this.closing = closing;
    }

    @GetMapping("/missions/{id}/invoice")
    public InvoicesView get(@PathVariable Long id) {
        return service.view(id);
    }

    @PostMapping("/missions/{id}/invoice/receive")
    public InvoicesView receive(@PathVariable Long id) {
        return service.receive(id);
    }

    @PostMapping("/missions/{id}/invoice/request-credit-note")
    public InvoicesView requestCreditNote(@PathVariable Long id) {
        return service.requestCreditNote(id);
    }

    @PostMapping("/missions/{id}/invoice/approve")
    public InvoicesView approve(@PathVariable Long id) {
        return service.approve(id);
    }

    @GetMapping(value = "/invoices/{invoiceId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable Long invoiceId) {
        Invoice invoice = service.pdfOf(invoiceId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + invoice.getRef() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(invoice.getPdf());
    }

    /** Stateless extraction of invoice text, used by the eval runner. */
    @PostMapping("/invoices/extract")
    public InvoiceLines extract(@RequestBody ExtractRequest request) {
        return service.extractText(request.text());
    }

    @GetMapping("/missions/{id}/summary")
    public Map<String, Object> summary(@PathVariable Long id) {
        return closing.summary(id);
    }
}
