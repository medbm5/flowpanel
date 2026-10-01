package com.flowpanel.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvoicePdfTest {

    @Test
    void renderedPdfTextIsParsedBackByTheMockExtractor() {
        byte[] pdf = InvoicePdf.render(new InvoicePdf.Document("INV-INTERSUD-0142", "InterSud Intérim", "LogiNord",
                "ORD-2026-0142", LocalDate.of(2026, 9, 21), LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 12),
                List.of(new InvoicePdf.Line("Karim Haddad", new BigDecimal("114"), new BigDecimal("13.20")),
                        new InvoicePdf.Line("Julien Marchand", new BigDecimal("105"), new BigDecimal("13.20")))));
        assertThat(pdf).startsWith("%PDF".getBytes());

        String text = InvoicePdf.text(pdf);
        assertThat(text).contains("FACTURE N° INV-INTERSUD-0142").contains("Karim Haddad | 114,00 h | 13,20 €/h | 1 504,80 €");

        InvoiceLines lines = MockInvoiceResponder.extract(text);
        assertThat(lines.invoiceNumber()).isEqualTo("INV-INTERSUD-0142");
        assertThat(lines.supplierName()).isEqualTo("InterSud Intérim");
        assertThat(lines.lines()).hasSize(2);
        assertThat(lines.lines().get(0).hours()).isEqualByComparingTo("114");
        assertThat(lines.lines().get(0).amount()).isEqualByComparingTo("1504.80");
        assertThat(lines.totalExclTax()).isEqualByComparingTo("2890.80");
        assertThat(lines.validationErrors()).isEmpty();
    }

    @Test
    void inconsistentAmountsFailValidation() {
        InvoiceLines bad = new InvoiceLines("X", "Y", List.of(new InvoiceLines.Line("A B", new BigDecimal("10"),
                new BigDecimal("13"), new BigDecimal("150"))), new BigDecimal("150"));
        assertThat(bad.validationErrors()).anyMatch(e -> e.contains("is not hours × rate"));
    }
}
