package com.flowpanel.invoice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpanel.ai.AiModelClient.ChatCall;
import com.flowpanel.ai.mock.MockResponder;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Mock profile answers for the invoice prompts: a rule-based parser of the PDF text ({@code invoice.extract}) and a
 * French credit-note request built from the match facts ({@code invoice.message}).
 */
@Component
public class MockInvoiceResponder implements MockResponder {

    private static final Pattern NUMBER = Pattern.compile("(?im)facture\\s*(?:n°|no|n|#)?\\s*:?\\s*([A-Z0-9][A-Z0-9-]+)");
    private static final Pattern SUPPLIER = Pattern.compile("(?im)^\\s*(?:fournisseur|émetteur|emetteur|supplier)\\s*:\\s*(.+)$");
    private static final Pattern TOTAL = Pattern.compile("(?im)total\\s*(?:ht|hors taxes?)\\s*:?\\s*([\\d  .,]+)\\s*€?");
    private static final String NUM = "(\\d[\\d  .]*(?:,\\d+)?|\\d+(?:\\.\\d+)?)";
    private static final Pattern LINE = Pattern.compile(
            "(?m)^\\s*(\\[PERSON_\\d+]|[\\p{L}][\\p{L}' .-]+?)\\s*[|;\\t]\\s*" + NUM + "\\s*h?\\s*[|;\\t]\\s*" + NUM
                    + "\\s*(?:€|eur)?\\s*(?:/\\s*h)?\\s*[|;\\t]\\s*" + NUM + "\\s*€?\\s*$");

    private final ObjectMapper mapper;

    public MockInvoiceResponder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<String> features() {
        return List.of(InvoiceService.EXTRACT_FEATURE, InvoiceService.MESSAGE_FEATURE);
    }

    @Override
    public String respond(ChatCall call, ToolRunner tools) {
        if (InvoiceService.MESSAGE_FEATURE.equals(call.feature())) {
            return message(call.facts());
        }
        try {
            return mapper.writeValueAsString(extract(call.user()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    static InvoiceLines extract(String text) {
        String number = find(NUMBER, text);
        String supplier = find(SUPPLIER, text);
        List<InvoiceLines.Line> lines = new ArrayList<>();
        Matcher m = LINE.matcher(text);
        while (m.find()) {
            String name = m.group(1).strip();
            if (name.equalsIgnoreCase("intérimaire") || name.equalsIgnoreCase("interimaire") || name.equalsIgnoreCase("worker")) {
                continue;
            }
            BigDecimal hours = number(m.group(2));
            BigDecimal rate = number(m.group(3));
            BigDecimal amount = number(m.group(4)).setScale(2, RoundingMode.HALF_UP);
            lines.add(new InvoiceLines.Line(name, hours, rate, amount));
        }
        String total = find(TOTAL, text);
        BigDecimal totalValue = total.isEmpty()
                ? lines.stream().map(InvoiceLines.Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add)
                : number(total).setScale(2, RoundingMode.HALF_UP);
        return new InvoiceLines(number, supplier, lines, totalValue);
    }

    @SuppressWarnings("unchecked")
    private static String message(Map<String, Object> facts) {
        List<Map<String, Object>> gaps = (List<Map<String, Object>>) facts.getOrDefault("gaps", List.of());
        StringBuilder sb = new StringBuilder("Bonjour,\n\nAprès rapprochement de votre facture ").append(facts.get("invoice"))
                .append(" (mission ").append(facts.get("mission")).append(") avec le contrat et les relevés d'heures validés, ")
                .append("nous constatons ").append(gaps.size() == 1 ? "un écart" : "des écarts").append(" :\n");
        for (Map<String, Object> g : gaps) {
            sb.append("- ").append(g.get("worker")).append(" : ").append(g.get("detail")).append(", soit ")
                    .append(g.get("delta")).append(" € HT facturés en trop.\n");
        }
        sb.append("\nPourriez-vous nous adresser un avoir de ").append(facts.get("creditNote"))
                .append(" € HT ? Nous validerons la facture dès sa réception.\n\nCordialement,\n").append(facts.get("buyer"));
        return sb.toString();
    }

    private static String find(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1).strip() : "";
    }

    static BigDecimal number(String raw) {
        String s = raw.replace(" ", "").replace(" ", "").strip();
        if (s.contains(",")) {
            s = s.replace(".", "").replace(',', '.');
        }
        return new BigDecimal(s);
    }
}
