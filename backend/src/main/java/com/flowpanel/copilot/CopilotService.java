package com.flowpanel.copilot;

import com.flowpanel.ai.AiGateway;
import com.flowpanel.ai.AiPrompt;
import com.flowpanel.ai.AiResult;
import com.flowpanel.audit.AuditService;
import com.flowpanel.auth.RequestContext;
import com.flowpanel.common.BadRequestException;
import com.flowpanel.copilot.DocumentService.Passage;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Copilot: tool-calling assistant over the caller's scope, plus document Q&A with verified citations.
 * Every citation must point to a passage retrieved in this request; otherwise the answer is dropped.
 */
@Service
@Transactional
public class CopilotService {

    public static final String FEATURE = "copilot.answer";
    public static final String NOT_FOUND = "Not found in your documents.";
    private static final Pattern CITATION = Pattern.compile("\\[(\\d{1,2})]");

    static final String SYSTEM_PROMPT = """
            You are the Flowpanel copilot for a temporary staffing platform. Today is %s.
            - Answer only from tool results. Never guess numbers.
            - For questions about missions, spend, rates, anomalies or contracts, call the matching tool.
            - For questions about policies, rules or agreements, call searchDocuments and cite every fact with the passage
              number in square brackets, e.g. [1]. If the passages do not contain the answer, reply exactly: "%s"
            - You only see data the user is allowed to see. Never claim to know about other organizations or suppliers.
            - Answer in the language of the question, in at most 4 sentences.""";

    public record Citation(int n, Long documentId, String documentTitle, String heading, String excerpt) {
    }

    public record ToolCallView(String name, Object arguments, Object result) {
    }

    public record CopilotAnswer(String question, String answer, boolean notFound, List<Citation> citations,
                                List<Citation> sources, List<ToolCallView> toolCalls, String model, long latencyMs) {
    }

    private final AiGateway gateway;
    private final CopilotTools tools;
    private final AuditService audit;
    private final RequestContext context;

    public CopilotService(AiGateway gateway, CopilotTools tools, AuditService audit, RequestContext context) {
        this.gateway = gateway;
        this.tools = tools;
        this.audit = audit;
        this.context = context;
    }

    public CopilotAnswer ask(String question) {
        if (question == null || question.isBlank()) {
            throw new BadRequestException("question is required");
        }
        if (question.length() > 1000) {
            throw new BadRequestException("question is too long (max 1000 characters)");
        }
        context.current();
        CopilotTools.PassageRegistry registry = new CopilotTools.PassageRegistry();
        AiPrompt prompt = AiPrompt.of(FEATURE, null, SYSTEM_PROMPT.formatted(LocalDate.now(), NOT_FOUND), question.strip())
                .withFacts(Map.of("today", LocalDate.now().toString()))
                .withMaxTokens(400);
        AiResult<String> result = gateway.withTools(prompt, tools.tools(registry));

        List<Passage> sources = registry.all();
        List<Citation> sourceViews = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            sourceViews.add(citation(i + 1, sources.get(i)));
        }
        Verified verified = verify(result.value(), sources.size());
        boolean dataToolUsed = result.toolCalls().stream().anyMatch(t -> !CopilotTools.SEARCH.equals(t.name()));
        String answer = verified.text();
        boolean notFound = false;
        if (!dataToolUsed && verified.valid().isEmpty()) {
            answer = NOT_FOUND;
            notFound = true;
        }
        List<Citation> citations = verified.valid().stream().map(n -> sourceViews.get(n - 1)).toList();
        List<ToolCallView> calls = result.toolCalls().stream()
                .map(t -> new ToolCallView(t.name(), t.arguments(), t.result())).toList();
        audit.human(null, "copilot.asked", "Asked the copilot: " + truncate(question, 120),
                Map.of("tools", calls.stream().map(ToolCallView::name).toList(), "citations", citations.size(),
                        "notFound", notFound, "aiCallId", result.aiCallId()));
        return new CopilotAnswer(question, answer, notFound, notFound ? List.of() : citations, sourceViews, calls,
                result.model(), result.latencyMs());
    }

    record Verified(String text, List<Integer> valid) {
    }

    /** Keeps citations that refer to a retrieved passage and strips the others from the text. */
    static Verified verify(String answer, int sourceCount) {
        String text = answer == null ? "" : answer;
        Set<Integer> valid = new LinkedHashSet<>();
        Matcher m = CITATION.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            int n = Integer.parseInt(m.group(1));
            if (n >= 1 && n <= sourceCount) {
                valid.add(n);
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group()));
            } else {
                m.appendReplacement(sb, "");
            }
        }
        m.appendTail(sb);
        String cleaned = sb.toString().replaceAll(" +([.,;])", "$1").replaceAll(" {2,}", " ").strip();
        if (cleaned.equals(NOT_FOUND) || cleaned.isEmpty()) {
            valid.clear();
        }
        return new Verified(cleaned, List.copyOf(valid));
    }

    private static Citation citation(int n, Passage p) {
        return new Citation(n, p.documentId(), p.documentTitle(), p.heading(), truncate(p.content(), 400));
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
