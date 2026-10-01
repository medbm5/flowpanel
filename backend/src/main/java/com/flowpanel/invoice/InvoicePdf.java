package com.flowpanel.invoice;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;

/** Writes synthetic supplier invoices as PDF (PDFBox) and extracts their text back. */
public final class InvoicePdf {

    public record Line(String workerName, BigDecimal hours, BigDecimal rate) {

        public BigDecimal amount() {
            return hours.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        }
    }

    public record Document(String number, String supplier, String client, String missionRef, LocalDate periodStart,
                           LocalDate periodEnd, LocalDate issueDate, List<Line> lines) {

        public BigDecimal total() {
            return lines.stream().map(Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    private static final DateTimeFormatter FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private InvoicePdf() {
    }

    public static byte[] render(Document d) {
        List<String> rows = new ArrayList<>();
        rows.add("FACTURE N° " + d.number());
        rows.add("");
        rows.add("Fournisseur : " + d.supplier());
        rows.add("Client : " + d.client());
        rows.add("Mission : " + d.missionRef());
        rows.add("Période : " + FR.format(d.periodStart()) + " au " + FR.format(d.periodEnd()));
        rows.add("Date d'émission : " + FR.format(d.issueDate()));
        rows.add("");
        rows.add("Intérimaire | Heures | Taux horaire | Montant HT");
        for (Line l : d.lines()) {
            rows.add(l.workerName() + " | " + money(l.hours()) + " h | " + money(l.rate()) + " €/h | " + money(l.amount()) + " €");
        }
        rows.add("");
        BigDecimal total = d.total();
        BigDecimal vat = total.multiply(new BigDecimal("0.20")).setScale(2, RoundingMode.HALF_UP);
        rows.add("Total HT : " + money(total) + " €");
        rows.add("TVA 20 % : " + money(vat) + " €");
        rows.add("Total TTC : " + money(total.add(vat)) + " €");
        rows.add("");
        rows.add("Paiement à 30 jours. Document de démonstration, données fictives.");

        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setLeading(16);
                cs.newLineAtOffset(56, 780);
                for (int i = 0; i < rows.size(); i++) {
                    cs.setFont(i == 0 ? bold : regular, i == 0 ? 15 : 11);
                    cs.showText(rows.get(i));
                    cs.newLine();
                }
                cs.endText();
            }
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String text(byte[] pdf) {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc).replace("\r\n", "\n").strip();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** French amount format with a space as thousands separator, e.g. 1 425,60. */
    static String money(BigDecimal value) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.FRANCE);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.00", symbols).format(value);
    }
}
