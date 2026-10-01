package com.flowpanel.copilot;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a document on headings first, then packs paragraphs into chunks of about {@code maxTokens}, with an overlap
 * of about {@code overlapTokens} between consecutive chunks of the same section. Tokens are estimated as chars / 4.
 */
public final class Chunker {

    public record Chunk(int index, String heading, String content, int tokens) {
    }

    public static final int MAX_TOKENS = 500;
    public static final int OVERLAP_TOKENS = 60;

    private Chunker() {
    }

    public static List<Chunk> chunk(String text) {
        return chunk(text, MAX_TOKENS, OVERLAP_TOKENS);
    }

    public static List<Chunk> chunk(String text, int maxTokens, int overlapTokens) {
        int maxChars = maxTokens * 4;
        int overlapChars = overlapTokens * 4;
        List<Chunk> chunks = new ArrayList<>();
        String title = null;
        String heading = null;
        StringBuilder section = new StringBuilder();
        for (String line : text.replace("\r\n", "\n").split("\n")) {
            if (line.startsWith("# ")) {
                title = line.substring(2).strip();
                continue;
            }
            if (line.matches("^#{2,6} .*")) {
                flush(chunks, title, heading, section.toString(), maxChars, overlapChars);
                section.setLength(0);
                heading = line.replaceFirst("^#+ ", "").strip();
                continue;
            }
            section.append(line).append('\n');
        }
        flush(chunks, title, heading, section.toString(), maxChars, overlapChars);
        return chunks;
    }

    private static void flush(List<Chunk> chunks, String title, String heading, String body, int maxChars, int overlapChars) {
        String clean = body.strip();
        if (clean.isEmpty()) {
            return;
        }
        String label = heading == null ? title : heading;
        String prefix = label == null ? "" : label + "\n";
        List<String> pieces = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : clean.split("\n\\s*\n")) {
            for (String part : split(paragraph.strip(), maxChars)) {
                if (current.length() > 0 && current.length() + part.length() + 2 > maxChars) {
                    pieces.add(current.toString());
                    String tail = current.length() > overlapChars ? current.substring(current.length() - overlapChars) : current.toString();
                    current = new StringBuilder(tail.substring(Math.max(0, tail.indexOf(' ') + 1)));
                }
                if (current.length() > 0) {
                    current.append("\n\n");
                }
                current.append(part);
            }
        }
        if (current.length() > 0) {
            pieces.add(current.toString());
        }
        for (String piece : pieces) {
            String content = prefix + piece;
            chunks.add(new Chunk(chunks.size(), label, content, (content.length() + 3) / 4));
        }
    }

    /** Splits an over-long paragraph on sentence boundaries. */
    private static List<String> split(String paragraph, int maxChars) {
        if (paragraph.length() <= maxChars) {
            return List.of(paragraph);
        }
        List<String> parts = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (String sentence : paragraph.split("(?<=[.!?])\\s+")) {
            if (sb.length() + sentence.length() + 1 > maxChars && sb.length() > 0) {
                parts.add(sb.toString().strip());
                sb.setLength(0);
            }
            sb.append(sentence).append(' ');
        }
        if (sb.length() > 0) {
            parts.add(sb.toString().strip());
        }
        return parts;
    }
}
