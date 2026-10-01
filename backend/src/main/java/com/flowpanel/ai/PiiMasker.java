package com.flowpanel.ai;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces emails, phone numbers and known person names with tokens ({@code [PERSON_1]}, {@code [EMAIL_1]},
 * {@code [PHONE_1]}) before text is sent to an LLM provider, and restores them in the provider's answer.
 * One {@link Session} per call keeps the token mapping consistent across system prompt, user prompt, facts and
 * tool results.
 */
public final class PiiMasker {

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+33\\s?|0)[1-9](?:[\\s.-]?\\d{2}){4}(?!\\d)");
    private static final Pattern TOKEN = Pattern.compile("\\[(PERSON|EMAIL|PHONE)_(\\d+)]");
    private static final String NOT_LETTER_BEFORE = "(?<![\\p{L}\\p{N}])";
    private static final String NOT_LETTER_AFTER = "(?![\\p{L}\\p{N}])";
    private static final String TITLES = "(?:M\\.|Mr|Mme|Mlle|Madame|Monsieur|Mrs|Ms)";
    private static final String GREETINGS = "(?:Bonjour|Bonsoir|Merci|Salut|Hello|Hi|Dear|Cher|Chère)";

    private PiiMasker() {
    }

    public static Session session(Collection<String> knownNames) {
        return new Session(knownNames);
    }

    public static final class Session {

        private final List<NamePattern> namePatterns = new ArrayList<>();
        private final Map<String, String> tokenToValue = new LinkedHashMap<>();
        private final Map<String, String> valueToToken = new LinkedHashMap<>();
        private int persons;
        private int emails;
        private int phones;

        private record NamePattern(Pattern pattern, String canonical) {
        }

        private Session(Collection<String> knownNames) {
            Set<String> names = new LinkedHashSet<>();
            for (String n : knownNames) {
                if (n != null && n.strip().contains(" ")) {
                    names.add(n.strip());
                }
            }
            List<String> sorted = new ArrayList<>(names);
            sorted.sort(Comparator.comparingInt(String::length).reversed());
            for (String full : sorted) {
                int space = full.indexOf(' ');
                String first = full.substring(0, space);
                String last = full.substring(space + 1);
                List<String> variants = List.of(
                        Pattern.quote(full),
                        Pattern.quote(last + " " + first),
                        Pattern.quote(last.toUpperCase() + " " + first),
                        TITLES + "\\s+" + Pattern.quote(last),
                        "(?<=" + GREETINGS + "\\s)" + Pattern.quote(first));
                for (String v : variants) {
                    namePatterns.add(new NamePattern(Pattern.compile(NOT_LETTER_BEFORE + v + NOT_LETTER_AFTER), full));
                }
            }
        }

        public String mask(String text) {
            if (text == null || text.isEmpty()) {
                return text;
            }
            String result = replace(text, EMAIL, "EMAIL", null);
            result = replace(result, PHONE, "PHONE", null);
            for (NamePattern np : namePatterns) {
                result = replace(result, np.pattern(), "PERSON", np.canonical());
            }
            return result;
        }

        /** Masks every string inside a facts structure (maps, lists, strings). */
        public Object maskValue(Object value) {
            if (value instanceof String s) {
                return mask(s);
            }
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> out = new LinkedHashMap<>();
                map.forEach((k, v) -> out.put(String.valueOf(k), maskValue(v)));
                return out;
            }
            if (value instanceof Collection<?> list) {
                List<Object> out = new ArrayList<>();
                list.forEach(v -> out.add(maskValue(v)));
                return out;
            }
            return value;
        }

        @SuppressWarnings("unchecked")
        public Map<String, Object> maskFacts(Map<String, Object> facts) {
            return (Map<String, Object>) maskValue(facts);
        }

        public String unmask(String text) {
            if (text == null || tokenToValue.isEmpty()) {
                return text;
            }
            Matcher m = TOKEN.matcher(text);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String original = tokenToValue.getOrDefault(m.group(), m.group());
                m.appendReplacement(sb, Matcher.quoteReplacement(original));
            }
            m.appendTail(sb);
            return sb.toString();
        }

        private String replace(String text, Pattern pattern, String kind, String canonical) {
            Matcher m = pattern.matcher(text);
            if (!m.find()) {
                return text;
            }
            m.reset();
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String value = canonical != null ? canonical : m.group();
                String token = valueToToken.computeIfAbsent(value, v -> {
                    String t = "[" + kind + "_" + next(kind) + "]";
                    tokenToValue.put(t, v);
                    return t;
                });
                m.appendReplacement(sb, Matcher.quoteReplacement(token));
            }
            m.appendTail(sb);
            return sb.toString();
        }

        private int next(String kind) {
            return switch (kind) {
                case "PERSON" -> ++persons;
                case "EMAIL" -> ++emails;
                default -> ++phones;
            };
        }
    }
}
