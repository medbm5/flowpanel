package com.flowpanel.intake;

import com.flowpanel.intake.OrderExtraction.ExtractedField;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-based extraction of a staffing order from a French email. Used by the mock AI profile as a deterministic
 * stand-in for the LLM. Values hedged in the email ("normalement", "environ", "dès que possible"...) get a low
 * confidence, which is what drives the human review step.
 */
public final class HeuristicOrderExtractor {

    static final double HIGH = 0.92;
    static final double HEDGED = 0.55;

    private static final String NUMBER_WORDS = "un|une|deux|trois|quatre|cinq|six|sept|huit|neuf|dix|douze|quinze|vingt";
    private static final Map<String, Integer> WORD_NUMBERS = Map.ofEntries(Map.entry("un", 1), Map.entry("une", 1),
            Map.entry("deux", 2), Map.entry("trois", 3), Map.entry("quatre", 4), Map.entry("cinq", 5), Map.entry("six", 6),
            Map.entry("sept", 7), Map.entry("huit", 8), Map.entry("neuf", 9), Map.entry("dix", 10), Map.entry("douze", 12),
            Map.entry("quinze", 15), Map.entry("vingt", 20));
    private static final Map<String, Integer> MONTHS = Map.ofEntries(Map.entry("janvier", 1), Map.entry("fevrier", 2),
            Map.entry("mars", 3), Map.entry("avril", 4), Map.entry("mai", 5), Map.entry("juin", 6), Map.entry("juillet", 7),
            Map.entry("aout", 8), Map.entry("septembre", 9), Map.entry("octobre", 10), Map.entry("novembre", 11),
            Map.entry("decembre", 12));
    private static final String[] DAY_ABBR = {"lun", "mar", "mer", "jeu", "ven", "sam", "dim"};
    private static final List<String> DAYS = List.of("lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche");

    private static final Pattern HEDGE = Pattern.compile(
            "(?iu)\\b(normalement|à confirmer|a confirmer|idéalement|idealement|dès que possible|des que possible|environ|"
                    + "autour de|approximativement|probablement|si possible|sous réserve|peut-être|plus ou moins|à peu près)\\b");
    private static final Pattern QUANTITY = Pattern.compile(
            "(?iu)(?:besoin de|cherchons|recherchons|il nous faut|il faudrait|recruter|souhaitons|demandons|faut)\\s+"
                    + "(\\d{1,2}|" + NUMBER_WORDS + ")\\s+([^\\n]+)");
    private static final Pattern REPLACE_POSITION = Pattern.compile(
            "(?iu)remplacer\\s+(\\[PERSON_\\d+]|[\\p{Lu}][\\p{L}-]+\\s+[\\p{Lu}][\\p{L}-]+)\\s*,\\s*([^,\\n]+?)\\s*,");
    private static final Pattern POSITION_LINE = Pattern.compile("(?iu)\\bposte\\s*:\\s*([^\\n,.]+)");
    private static final Pattern POSITION_STOP = Pattern.compile(
            "(?iu)\\s+(?:pour|sur|au|aux|à|a|dans|chez|du|dès|des le|en|afin|entre|le|la|qui|from|for|at)\\s|[,.;:(\\n]");
    private static final Pattern CERT = Pattern.compile(
            "(?iu)CACES\\s*(?:R\\s?(4\\d{2})\\s*)?(?:cat(?:égorie|egorie)?\\.?\\s*)?(\\d+)|\\b(SST)\\b|habilitation électrique\\s*(\\w+)?");
    private static final Pattern OPTIONAL = Pattern.compile(
            "(?iu)apprécié|apprecie|souhaité|souhaite|pas obligatoire|optionnel|un plus|bienvenu");
    private static final Pattern SITE = Pattern.compile(
            "(?iu)(?<![\\p{L}])(entrepôt|entrepot|plateforme|site|usine|siège|siege|atelier|agence|magasin|dépôt|depot|bureaux|centre)(?![\\p{L}])");
    private static final Pattern SITE_STOP = Pattern.compile(
            "(?iu),|\\.(?:\\s|$)|\\s+(?:du|pour|a besoin|à partir|a partir|dès|des|en|entre|cherche|recherche|recrute|nous)\\s|\\n|\\s\\(");
    private static final Pattern DATE = Pattern.compile(
            "(?iu)(\\d{1,2})(?:er)?\\s+(janvier|février|fevrier|mars|avril|mai|juin|juillet|août|aout|septembre|octobre|novembre|décembre|decembre)\\s+(\\d{4})"
                    + "|(\\d{1,2})/(\\d{1,2})/(\\d{4})|(\\d{4})-(\\d{2})-(\\d{2})");
    private static final Pattern END_TRIGGER = Pattern.compile(
            "(?iu)(?:jusqu['’]au|jusqu['’]à|au|until|to)\\s+(?:(?:lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche)\\s+)?$");
    private static final Pattern START_TRIGGER = Pattern.compile(
            "(?iu)(?:à partir du|a partir du|à compter du|a compter du|dès le|des le|du|le|from|starting)\\s+"
                    + "(?:(?:lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche)\\s+)?$");
    private static final Pattern DAY_RANGE = Pattern.compile(
            "(?iu)du\\s+(lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche)\\s+au\\s+(lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche)");
    private static final Pattern TIME_RANGE = Pattern.compile(
            "(?iu)(\\d{1,2})\\s*h\\s*(\\d{2})?\\s*(?:-|–|à|a)\\s*(\\d{1,2})\\s*h\\s*(\\d{2})?");
    private static final Pattern WEEKLY_HOURS = Pattern.compile(
            "(?iu)(\\d{1,2}(?:[.,]\\d+)?)\\s*h(?:eures)?\\.?\\s*(?:par semaine|hebdo(?:madaires?)?|/\\s*semaine|par sem\\.?)");
    private static final Pattern MONEY = Pattern.compile("(?iu)(\\d{1,3}(?:[.,]\\d{1,2})?)\\s*(?:€|euros?|eur\\b)");
    private static final Pattern RATE_CONTEXT = Pattern.compile(
            "(?iu)taux|rémunération|remuneration|salaire|payé|paye|brut|de l['’]heure|horaire|/\\s*h\\b|tarif");
    private static final Pattern REASON_LINE = Pattern.compile("(?iu)^\\s*motif\\s*:\\s*(.+)$", Pattern.MULTILINE);
    private static final Pattern PERSON_TOKEN = Pattern.compile("\\[PERSON_\\d+]");
    private static final Pattern REPLACED_NAME = Pattern.compile(
            "(?iu)remplac(?:er|ement de)\\s+(\\[PERSON_\\d+]|(?:M\\.|Mme|Madame|Monsieur)?\\s*[\\p{Lu}][\\p{L}-]+\\s+[\\p{Lu}][\\p{L}-]+)");

    private HeuristicOrderExtractor() {
    }

    public static OrderExtraction extract(String email) {
        String text = email == null ? "" : email.replace(' ', ' ').replace('’', '\'');
        String body = stripSignature(text);
        Position position = position(body);
        String reason = legalReason(body);
        ExtractedField reasonField = reason.isEmpty() ? new ExtractedField("", 0.3)
                : new ExtractedField(reason, REASON_LINE.matcher(body).find() ? HIGH : 0.75);
        Dates dates = dates(body);
        Schedule schedule = schedule(body);
        return new OrderExtraction(
                new ExtractedField(position.title, position.confidence),
                new ExtractedField(position.quantity, position.quantityConfidence),
                site(body),
                dates.start,
                dates.end,
                new ExtractedField(schedule.text, schedule.confidence),
                weeklyHours(body, schedule),
                rate(body),
                reasonField,
                replacedEmployee(body, reason),
                certifications(body),
                overtime(body));
    }

    // ----------------------------------------------------------------- position & quantity

    private record Position(String title, double confidence, String quantity, double quantityConfidence) {
    }

    private static Position position(String body) {
        Matcher q = QUANTITY.matcher(body);
        if (q.find()) {
            int qty = toNumber(q.group(1));
            String title = cleanPosition(q.group(2));
            return new Position(title, title.isEmpty() ? 0.3 : HIGH, String.valueOf(qty), HIGH);
        }
        Matcher r = REPLACE_POSITION.matcher(body);
        if (r.find()) {
            return new Position(cleanPosition(r.group(2)), 0.85, "1", 0.85);
        }
        Matcher p = POSITION_LINE.matcher(body);
        if (p.find()) {
            return new Position(cleanPosition(p.group(1)), 0.8, "1", 0.6);
        }
        return new Position("", 0.2, "", 0.2);
    }

    static String cleanPosition(String raw) {
        String s = CERT.matcher(raw).replaceAll(" ")
                .replaceAll("(?iu)(?<!\\p{L})(obligatoires?|exigée?s?|requise?s?|indispensables?|souhaitée?s?|apprécié(?:e|s)?)(?!\\p{L})", " ");
        Matcher stop = POSITION_STOP.matcher(s);
        if (stop.find()) {
            s = s.substring(0, stop.start());
        }
        s = s.replaceAll("(?iu)\\(?\\s*h\\s*/\\s*f\\s*\\)?", " ").replaceAll("\\s+", " ").strip();
        if (s.isEmpty()) {
            return "";
        }
        String[] words = s.split(" ");
        Set<String> prepositions = Set.of("de", "d'", "du", "des", "en", "à", "a", "pour", "et");
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            if (prepositions.contains(w.toLowerCase(Locale.ROOT)) || w.toLowerCase(Locale.ROOT).startsWith("d'")) {
                break;
            }
            words[i] = singular(w);
        }
        String joined = String.join(" ", words);
        return Character.toUpperCase(joined.charAt(0)) + joined.substring(1);
    }

    private static String singular(String w) {
        String lower = w.toLowerCase(Locale.ROOT);
        if (w.length() > 3 && (lower.endsWith("s") || lower.endsWith("x")) && !lower.endsWith("ss")) {
            return w.substring(0, w.length() - 1);
        }
        return w;
    }

    private static int toNumber(String token) {
        String t = token.toLowerCase(Locale.ROOT);
        return WORD_NUMBERS.containsKey(t) ? WORD_NUMBERS.get(t) : Integer.parseInt(t);
    }

    // ----------------------------------------------------------------- site

    /** Looks in the email body first, then in the subject line. */
    private static ExtractedField site(String body) {
        String withoutSubject = body.replaceFirst("(?im)^\\s*(?:objet|subject)\\s*:.*$", "");
        ExtractedField fromBody = siteIn(withoutSubject);
        return fromBody.value().isEmpty() ? siteIn(body) : fromBody;
    }

    private static ExtractedField siteIn(String body) {
        Matcher m = SITE.matcher(body);
        while (m.find()) {
            String rest = body.substring(m.end());
            Matcher stop = SITE_STOP.matcher(rest);
            String tail = (stop.find() ? rest.substring(0, stop.start()) : rest).strip();
            if (tail.isEmpty() || tail.startsWith("[") || !tail.matches("(?su).*\\p{Lu}.*") || tail.length() > 60) {
                continue;
            }
            String keyword = m.group(1).toLowerCase(Locale.ROOT);
            String name = Character.toUpperCase(keyword.charAt(0)) + keyword.substring(1) + " " + tail;
            return new ExtractedField(name.replaceAll("\\s+", " "), HIGH);
        }
        return new ExtractedField("", 0.2);
    }

    // ----------------------------------------------------------------- dates

    private record Dates(ExtractedField start, ExtractedField end) {
    }

    private record DateHit(String iso, int triggerStart, int start, int end, boolean endTrigger, boolean startTrigger) {
    }

    private static Dates dates(String body) {
        List<DateHit> hits = new ArrayList<>();
        Matcher m = DATE.matcher(body);
        while (m.find()) {
            String iso = iso(m);
            if (iso == null) {
                continue;
            }
            String prefix = body.substring(Math.max(0, m.start() - 30), m.start());
            Matcher endT = END_TRIGGER.matcher(prefix);
            Matcher startT = START_TRIGGER.matcher(prefix);
            boolean isEnd = endT.find();
            boolean isStart = !isEnd && startT.find();
            int trigger = isEnd ? m.start() - (prefix.length() - endT.start())
                    : isStart ? m.start() - (prefix.length() - startT.start()) : m.start();
            hits.add(new DateHit(iso, trigger, m.start(), m.end(), isEnd, isStart));
        }
        if (hits.isEmpty()) {
            return new Dates(new ExtractedField("", 0.2), new ExtractedField("", 0.2));
        }
        int startIdx = -1;
        int endIdx = -1;
        for (int i = 0; i < hits.size(); i++) {
            if (hits.get(i).endTrigger() && endIdx < 0 && startIdx >= 0) {
                endIdx = i;
            } else if (!hits.get(i).endTrigger() && startIdx < 0) {
                startIdx = i;
            }
        }
        if (startIdx < 0) {
            startIdx = 0;
        }
        if (endIdx < 0 && hits.size() > startIdx + 1) {
            endIdx = startIdx + 1;
        }
        ExtractedField start = new ExtractedField(hits.get(startIdx).iso(), confidenceAround(body, hits, startIdx));
        ExtractedField end = endIdx < 0 ? new ExtractedField("", 0.3)
                : new ExtractedField(hits.get(endIdx).iso(), confidenceAround(body, hits, endIdx));
        return new Dates(start, end);
    }

    /** Looks for hedging words in the clause of a date, bounded by punctuation and by the neighbouring dates. */
    private static double confidenceAround(String body, List<DateHit> hits, int i) {
        DateHit hit = hits.get(i);
        int from = lastBoundaryBefore(body, hit.triggerStart());
        if (i > 0) {
            from = Math.max(from, hits.get(i - 1).end());
        }
        int to = nextBoundaryAfter(body, hit.end());
        if (i + 1 < hits.size()) {
            to = Math.min(to, hits.get(i + 1).triggerStart());
        }
        return HEDGE.matcher(body.substring(from, Math.max(from, to))).find() ? HEDGED : HIGH;
    }

    private static int lastBoundaryBefore(String s, int index) {
        for (int i = index - 1; i >= 0; i--) {
            char c = s.charAt(i);
            if (c == ',' || c == ';' || c == '\n' || (c == '.' && i + 1 < s.length() && Character.isWhitespace(s.charAt(i + 1)))) {
                return i + 1;
            }
        }
        return 0;
    }

    private static int nextBoundaryAfter(String s, int index) {
        for (int i = index; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ',' || c == ';' || c == '\n' || (c == '.' && (i + 1 == s.length() || Character.isWhitespace(s.charAt(i + 1))))) {
                return i;
            }
        }
        return s.length();
    }

    private static String iso(Matcher m) {
        int day;
        int month;
        int year;
        if (m.group(1) != null) {
            day = Integer.parseInt(m.group(1));
            month = MONTHS.get(fold(m.group(2)));
            year = Integer.parseInt(m.group(3));
        } else if (m.group(4) != null) {
            day = Integer.parseInt(m.group(4));
            month = Integer.parseInt(m.group(5));
            year = Integer.parseInt(m.group(6));
        } else {
            year = Integer.parseInt(m.group(7));
            month = Integer.parseInt(m.group(8));
            day = Integer.parseInt(m.group(9));
        }
        if (month < 1 || month > 12 || day < 1 || day > 31) {
            return null;
        }
        return String.format("%04d-%02d-%02d", year, month, day);
    }

    // ----------------------------------------------------------------- schedule, hours, rate

    private record Schedule(String text, double confidence, Double dailyHours, int days) {
    }

    private static Schedule schedule(String body) {
        Matcher d = DAY_RANGE.matcher(body);
        Matcher t = TIME_RANGE.matcher(body);
        boolean hasDays = d.find();
        boolean hasTime = t.find();
        String days = "";
        int dayCount = 5;
        if (hasDays) {
            int a = DAYS.indexOf(d.group(1).toLowerCase(Locale.ROOT));
            int b = DAYS.indexOf(d.group(2).toLowerCase(Locale.ROOT));
            dayCount = b - a + 1;
            days = capitalize(DAY_ABBR[a]) + "–" + DAY_ABBR[b];
        }
        String time = "";
        Double daily = null;
        if (hasTime) {
            int h1 = Integer.parseInt(t.group(1));
            int m1 = t.group(2) == null ? 0 : Integer.parseInt(t.group(2));
            int h2 = Integer.parseInt(t.group(3));
            int m2 = t.group(4) == null ? 0 : Integer.parseInt(t.group(4));
            time = String.format("%02d:%02d–%02d:%02d", h1, m1, h2, m2);
            daily = ((h2 * 60 + m2) - (h1 * 60 + m1)) / 60.0;
        }
        String text = (days + " " + time).strip();
        double confidence = hasDays && hasTime ? HIGH : hasDays || hasTime ? 0.6 : 0.2;
        return new Schedule(text, confidence, daily, dayCount);
    }

    private static ExtractedField weeklyHours(String body, Schedule schedule) {
        Matcher m = WEEKLY_HOURS.matcher(body);
        if (m.find()) {
            return new ExtractedField(number(m.group(1)), HIGH);
        }
        if (schedule.dailyHours() != null && schedule.dailyHours() > 0) {
            return new ExtractedField(number(String.valueOf(schedule.dailyHours() * schedule.days())), 0.7);
        }
        return new ExtractedField("", 0.2);
    }

    private static ExtractedField rate(String body) {
        Matcher m = MONEY.matcher(body);
        String fallback = null;
        while (m.find()) {
            String sentence = sentence(body, m.start());
            if (RATE_CONTEXT.matcher(sentence).find()) {
                return new ExtractedField(number(m.group(1)), HEDGE.matcher(sentence).find() ? HEDGED : HIGH);
            }
            if (fallback == null) {
                fallback = m.group(1);
            }
        }
        return fallback == null ? new ExtractedField("", 0.2) : new ExtractedField(number(fallback), 0.6);
    }

    // ----------------------------------------------------------------- legal reason, replaced employee

    private static String legalReason(String body) {
        Matcher line = REASON_LINE.matcher(body);
        String scope = line.find() ? line.group(1) : body;
        String f = fold(scope);
        if (f.contains("remplacement") || f.contains("remplacer") || f.contains("absent") || f.contains("conge")) {
            return "REPLACEMENT";
        }
        if (f.contains("saison")) {
            return "SEASONAL";
        }
        if (f.contains("accroissement") || f.contains("surcroit") || f.contains("pic d") || f.contains("hausse d")) {
            return "ACTIVITY_INCREASE";
        }
        return "";
    }

    private static ExtractedField replacedEmployee(String body, String reason) {
        if (!"REPLACEMENT".equals(reason)) {
            return new ExtractedField("", 0.95);
        }
        Matcher m = REPLACED_NAME.matcher(body);
        if (m.find()) {
            return new ExtractedField(m.group(1).strip(), HIGH);
        }
        Matcher token = PERSON_TOKEN.matcher(body);
        return token.find() ? new ExtractedField(token.group(), 0.6) : new ExtractedField("", 0.3);
    }

    // ----------------------------------------------------------------- certifications, overtime

    private static ExtractedField certifications(String body) {
        Set<String> certs = new LinkedHashSet<>();
        Matcher m = CERT.matcher(body);
        while (m.find()) {
            if (OPTIONAL.matcher(line(body, m.start())).find()) {
                continue;
            }
            if (m.group(2) != null) {
                certs.add("CACES R" + (m.group(1) == null ? "489" : m.group(1)) + " cat. " + m.group(2));
            } else if (m.group(3) != null) {
                certs.add("SST");
            } else {
                certs.add("Habilitation électrique" + (m.group(4) == null ? "" : " " + m.group(4).toUpperCase(Locale.ROOT)));
            }
        }
        return new ExtractedField(String.join(", ", certs), certs.isEmpty() ? 0.85 : HIGH);
    }

    private static ExtractedField overtime(String body) {
        String f = fold(body);
        if (f.matches("(?s).*(pas d'heures sup|sans heures sup|heures supplementaires non prevues|aucune heure sup|pas d'heure sup|heures sup(plementaires)? non).*")) {
            return new ExtractedField("false", HIGH);
        }
        if (f.matches("(?s).*heures sup(plementaires)? (possibles|acceptees|prevues|a prevoir|autorisees).*")) {
            return new ExtractedField("true", 0.85);
        }
        return new ExtractedField("false", 0.8);
    }

    // ----------------------------------------------------------------- helpers

    private static String stripSignature(String text) {
        String[] markers = {"\nCordialement", "\nBien à vous", "\nBien a vous", "\nMerci,", "\nMerci d'avance", "\nBonne journée"};
        int cut = text.length();
        for (String marker : markers) {
            int i = text.indexOf(marker);
            if (i > 0) {
                cut = Math.min(cut, i);
            }
        }
        return text.substring(0, cut);
    }

    private static String line(String s, int index) {
        int from = s.lastIndexOf('\n', index) + 1;
        int to = s.indexOf('\n', index);
        return s.substring(from, to < 0 ? s.length() : to);
    }

    private static String sentence(String s, int index) {
        int from = index;
        while (from > 0 && s.charAt(from - 1) != '\n' && !(s.charAt(from - 1) == ' ' && from > 1 && s.charAt(from - 2) == '.')) {
            from--;
        }
        int to = index;
        while (to < s.length() && s.charAt(to) != '\n' && !(s.charAt(to) == '.' && (to + 1 == s.length() || s.charAt(to + 1) == ' ' || s.charAt(to + 1) == '\n'))) {
            to++;
        }
        return s.substring(from, to);
    }

    private static String number(String raw) {
        String n = raw.replace(',', '.');
        if (n.contains(".")) {
            n = n.replaceAll("0+$", "").replaceAll("\\.$", "");
            if (n.contains(".") && n.substring(n.indexOf('.') + 1).length() == 1) {
                n = n + "0";
            }
        }
        return n;
    }

    private static String capitalize(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static String fold(String s) {
        return Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
