package com.flowpanel.copilot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpanel.ai.AiModelClient.ChatCall;
import com.flowpanel.ai.mock.MockResponder;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Mock profile copilot: keyword routing to one tool, then an answer composed only from the tool result. For document
 * questions it quotes the best-matching sentence of the retrieved passages and cites it, like a grounded model would.
 */
@Component
public class MockCopilotResponder implements MockResponder {

    private static final Pattern REF = Pattern.compile("(?i)ORD-\\d{4}-\\d{4}");
    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Set<String> STOP = Set.of("the", "and", "for", "what", "which", "who", "how", "many", "much", "are",
            "is", "our", "does", "can", "with", "from", "that", "this", "there", "any", "about", "have", "has", "when", "must",
            "should", "will", "les", "des", "une", "est", "quel", "quelle", "pour", "dans", "sur", "avec", "nos", "notre",
            "combien", "comment", "quels", "quelles", "what's", "tell", "please", "give", "show", "list", "all", "do", "you");

    private final ObjectMapper mapper;

    public MockCopilotResponder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<String> features() {
        return List.of(CopilotService.FEATURE);
    }

    @Override
    public String respond(ChatCall call, ToolRunner tools) {
        String question = call.user();
        String q = fold(question);
        try {
            if (q.matches("(?s).*(spend|spent|depense|cost|cout|budget|rate|taux|tarif|how much|amount|montant).*")
                    && !q.matches("(?s).*(policy|politique|markup|coefficient|night|nuit|overtime|heures sup|bonus|agreement|accord|paid within|payment term).*")) {
                return spend(tools.call(CopilotTools.SPEND, Map.of()));
            }
            if (q.matches("(?s).*(anomal|ecart d'heures).*")) {
                return anomalies(tools.call(CopilotTools.ANOMALIES, Map.of()));
            }
            if (q.matches("(?s).*(contract|contrat).*(end|ending|expire|finish|termin|fin).*")) {
                return contracts(tools.call(CopilotTools.CONTRACTS_ENDING, Map.of("date", dateIn(question, call))));
            }
            Matcher ref = REF.matcher(question);
            if (ref.find()) {
                return status(tools.call(CopilotTools.MISSION_STATUS, Map.of("ref", ref.group().toUpperCase(Locale.ROOT))));
            }
            if (q.matches("(?s).*(mission|order|commande).*") && !q.matches("(?s).*(policy|rule|agreement|regle|accord).*")) {
                String status = q.contains("review") ? "review" : q.contains("closed") || q.contains("clotur") ? "closed"
                        : q.contains("open") || q.contains("progress") || q.contains("cours") ? "open" : "all";
                return missions(tools.call(CopilotTools.LIST_MISSIONS, Map.of("status", status)));
            }
            return documents(question, tools.call(CopilotTools.SEARCH, Map.of("query", question)));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ answers from tool results

    private String spend(String json) throws java.io.IOException {
        List<Map<String, Object>> rows = mapper.readValue(json, new TypeReference<>() { });
        if (rows.isEmpty()) {
            return "No spend or rate data is available in your scope.";
        }
        List<String> parts = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            parts.add(r.get("supplier") + ": " + r.get("contracts") + " contract(s), average rate €" + r.get("averageHourlyRate")
                    + "/h, spend €" + r.get("spend"));
        }
        return "Spend by supplier — " + String.join("; ", parts) + ".";
    }

    private String anomalies(String json) throws java.io.IOException {
        List<Map<String, Object>> rows = mapper.readValue(json, new TypeReference<>() { });
        if (rows.isEmpty()) {
            return "There are 0 open timesheet anomalies.";
        }
        List<String> parts = rows.stream().map(r -> r.get("mission") + " — " + r.get("worker") + ", week of " + r.get("weekStart")
                + ": " + r.get("anomaly")).toList();
        return "There " + (rows.size() == 1 ? "is 1 open timesheet anomaly" : "are " + rows.size() + " open timesheet anomalies")
                + ": " + String.join("; ", parts) + ".";
    }

    private String contracts(String json) throws java.io.IOException {
        List<Map<String, Object>> rows = mapper.readValue(json, new TypeReference<>() { });
        if (rows.isEmpty()) {
            return "No contract ends before that date (0 contracts).";
        }
        List<String> parts = rows.stream().map(r -> r.get("contract") + " (" + r.get("worker") + ", " + r.get("mission") + ") ends "
                + r.get("endDate")).toList();
        return rows.size() + " contract(s) end before that date: " + String.join("; ", parts) + ".";
    }

    private String status(String json) throws java.io.IOException {
        Map<String, Object> r = mapper.readValue(json, new TypeReference<>() { });
        if (r.containsKey("error")) {
            return "I can't find that mission in your scope.";
        }
        return r.get("ref") + " is in phase " + r.get("phase") + (r.containsKey("nextAction") ? "; next action: " + r.get("nextAction") : "")
                + (r.containsKey("positions") ? "; positions filled " + r.get("positions") : "") + ".";
    }

    private String missions(String json) throws java.io.IOException {
        List<Map<String, Object>> rows = mapper.readValue(json, new TypeReference<>() { });
        if (rows.isEmpty()) {
            return "You have 0 missions in this view.";
        }
        List<String> parts = rows.stream().map(r -> r.get("ref") + " " + r.get("title") + " (" + r.get("phase") + ")").toList();
        return "You have " + rows.size() + " mission(s): " + String.join("; ", parts) + ".";
    }

    @SuppressWarnings("unchecked")
    private String documents(String question, String json) throws java.io.IOException {
        Map<String, Object> result = mapper.readValue(json, new TypeReference<>() { });
        List<Map<String, Object>> passages = (List<Map<String, Object>>) result.getOrDefault("passages", List.of());
        Set<String> terms = terms(question);
        // A sentence must cover a good part of the question to count as an answer; the section heading helps ranking.
        long needed = Math.max(1, Math.round(Math.ceil(terms.size() * 0.4)));
        String best = null;
        int bestN = 0;
        double bestScore = 0;
        for (Map<String, Object> p : passages) {
            double passageScore = ((Number) p.get("score")).doubleValue();
            String section = String.valueOf(p.getOrDefault("section", ""));
            Set<String> headingTerms = terms(section);
            long headingOverlap = headingTerms.stream().filter(terms::contains).count();
            String text = String.valueOf(p.get("text"));
            for (String sentence : text.split("(?<=[.!?])\\s+|\\n+")) {
                String trimmed = sentence.strip();
                if (trimmed.length() < 15 || trimmed.equalsIgnoreCase(section)) {
                    continue;
                }
                long overlap = terms(trimmed).stream().filter(terms::contains).count();
                double score = overlap + 0.75 * headingOverlap + passageScore;
                if (overlap >= needed && score > bestScore) {
                    bestScore = score;
                    best = trimmed;
                    bestN = ((Number) p.get("n")).intValue();
                }
            }
        }
        if (best == null) {
            return CopilotService.NOT_FOUND;
        }
        return best + " [" + bestN + "]";
    }

    // ------------------------------------------------------------------ helpers

    private static String dateIn(String question, ChatCall call) {
        Matcher m = ISO_DATE.matcher(question);
        if (m.find()) {
            try {
                return LocalDate.parse(m.group()).toString();
            } catch (DateTimeParseException ignored) {
                // fall through
            }
        }
        String today = String.valueOf(call.facts().getOrDefault("today", LocalDate.now().toString()));
        return LocalDate.parse(today).plusDays(30).toString();
    }

    static Set<String> terms(String text) {
        Set<String> out = new HashSet<>();
        for (String w : fold(text).split("[^a-z0-9]+")) {
            if (w.length() >= 3 && !STOP.contains(w)) {
                out.add(stem(w));
            }
        }
        return out;
    }

    static String stem(String w) {
        if (w.length() > 5 && w.endsWith("ing")) {
            return w.substring(0, w.length() - 3);
        }
        if (w.length() > 4 && w.endsWith("ed")) {
            return w.substring(0, w.length() - 2);
        }
        if (w.length() > 4 && w.endsWith("s") && !w.endsWith("ss")) {
            return w.substring(0, w.length() - 1);
        }
        return w;
    }

    static String fold(String s) {
        return Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
