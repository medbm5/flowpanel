package com.flowpanel.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpanel.ai.AiModelClient.ChatCall;
import com.flowpanel.ai.AiModelClient.ChatOutcome;
import com.flowpanel.auth.RequestContext;
import jakarta.validation.Validation;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DefaultAiGatewayTest {

    record Answer(@JsonProperty(required = true) String name, @JsonProperty(required = true) @Positive int quantity)
            implements ValidatableOutput {
        @Override
        public List<String> validationErrors() {
            return name.isBlank() ? List.of("name is blank") : List.of();
        }
    }

    /** Stub provider that answers from a queue of canned outputs and keeps what it received. */
    static class StubClient implements AiModelClient {
        final List<String> outputs = new ArrayList<>();
        final List<ChatCall> received = new ArrayList<>();
        Function<ChatCall, String> dynamic;

        @Override
        public String profile() {
            return "stub";
        }

        @Override
        public String chatModel() {
            return "gpt-4o-mini";
        }

        @Override
        public String embeddingModel() {
            return "text-embedding-3-small";
        }

        @Override
        public ChatOutcome chat(ChatCall call) {
            received.add(call);
            String text = dynamic != null ? dynamic.apply(call) : outputs.remove(0);
            return new ChatOutcome(text, "gpt-4o-mini", 1000, 200, 50L);
        }

        @Override
        public EmbeddingOutcome embed(List<String> texts) {
            return new EmbeddingOutcome(texts.stream().map(t -> new float[] {1f}).toList(), "text-embedding-3-small", 10, 5L);
        }
    }

    StubClient client;
    AiCallRecorder recorder;
    SpendGuard guard;
    PiiDirectory directory;

    @BeforeEach
    void setUp() {
        client = new StubClient();
        recorder = mock(AiCallRecorder.class);
        when(recorder.record(any())).thenAnswer(inv -> {
            AiCallRecorder.Attempt a = inv.getArgument(0);
            return new AiCall(1L, null, 1L, null, a.feature(), a.kind(), "mock", a.model(), a.inputTokens(), a.outputTokens(),
                    a.latencyMs(), BigDecimal.ONE, a.status(), a.attempt(), a.error(), a.promptHash());
        });
        guard = mock(SpendGuard.class);
        when(guard.tryAcquire(any())).thenReturn(true);
        directory = mock(PiiDirectory.class);
        when(directory.knownNames()).thenReturn(List.of("Julie Perrin"));
    }

    private DefaultAiGateway gateway(String profile) {
        AiProperties props = new AiProperties(profile, "key", "gpt-4o-mini", "text-embedding-3-small", new BigDecimal("2"),
                30, 800, 0.75, Map.of());
        return new DefaultAiGateway(client, recorder, guard, directory, new RequestContext(), props,
                Validation.buildDefaultValidatorFactory().getValidator(), new ObjectMapper());
    }

    @Test
    void structuredOutputIsParsedAndRecorded() {
        client.outputs.add("{\"name\":\"Cariste\",\"quantity\":2}");
        AiResult<Answer> result = gateway("mock").structured(AiPrompt.of("test", 7L, "sys", "user"), Answer.class);
        assertThat(result.value()).isEqualTo(new Answer("Cariste", 2));
        assertThat(result.latencyMs()).isEqualTo(50L);
        ArgumentCaptor<AiCallRecorder.Attempt> captor = ArgumentCaptor.forClass(AiCallRecorder.Attempt.class);
        verify(recorder).record(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(AiCall.Status.OK);
        assertThat(captor.getValue().missionId()).isEqualTo(7L);
        assertThat(client.received.get(0).jsonSchema()).contains("quantity");
        assertThat(client.received.get(0).maxTokens()).isEqualTo(800);
    }

    @Test
    void invalidOutputIsRetriedOnceWithTheValidationError() {
        client.outputs.add("{\"name\":\"Cariste\",\"quantity\":0}");
        client.outputs.add("{\"name\":\"Cariste\",\"quantity\":3}");
        AiResult<Answer> result = gateway("mock").structured(AiPrompt.of("test", null, "sys", "user"), Answer.class);
        assertThat(result.value().quantity()).isEqualTo(3);
        assertThat(client.received).hasSize(2);
        assertThat(client.received.get(1).user()).contains("previous answer was rejected").contains("quantity");
        ArgumentCaptor<AiCallRecorder.Attempt> captor = ArgumentCaptor.forClass(AiCallRecorder.Attempt.class);
        verify(recorder, org.mockito.Mockito.times(2)).record(captor.capture());
        assertThat(captor.getAllValues()).extracting(AiCallRecorder.Attempt::status)
                .containsExactly(AiCall.Status.INVALID_OUTPUT, AiCall.Status.OK);
    }

    @Test
    void secondInvalidOutputFailsWithTypedError() {
        client.outputs.add("not json");
        client.outputs.add("{\"name\":\"\",\"quantity\":1}");
        assertThatThrownBy(() -> gateway("mock").structured(AiPrompt.of("intake.extract", null, "s", "u"), Answer.class))
                .isInstanceOf(AiException.class)
                .satisfies(e -> assertThat(((AiException) e).code()).isEqualTo("ai_invalid_output"));
    }

    @Test
    void piiIsMaskedBeforeTheProviderAndRestoredAfter() {
        client.dynamic = call -> "Remplacement de " + call.user().substring(call.user().indexOf("[PERSON_1]"), call.user().indexOf("[PERSON_1]") + 10);
        AiResult<String> result = gateway("mock").text(AiPrompt.of("test", null,
                "System for julie.perrin@corp.example", "Remplacer Julie Perrin (06 11 22 33 44)."));
        ChatCall sent = client.received.get(0);
        assertThat(sent.user()).doesNotContain("Julie", "Perrin", "06 11").contains("[PERSON_1]", "[PHONE_1]");
        assertThat(sent.system()).doesNotContain("julie.perrin@");
        assertThat(result.value()).isEqualTo("Remplacement de Julie Perrin");
    }

    @Test
    void liveCallsAreRefusedWhenTheDailyBudgetIsReached() {
        when(guard.budgetExhausted()).thenReturn(true);
        assertThatThrownBy(() -> gateway("live").text(AiPrompt.of("test", null, "s", "u")))
                .isInstanceOf(AiException.class)
                .satisfies(e -> assertThat(((AiException) e).code()).isEqualTo("ai_budget_exceeded"));
        assertThat(client.received).isEmpty();
        ArgumentCaptor<AiCallRecorder.Attempt> captor = ArgumentCaptor.forClass(AiCallRecorder.Attempt.class);
        verify(recorder).record(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(AiCall.Status.BUDGET_EXCEEDED);
    }

    @Test
    void liveCallsAreRateLimitedPerTenant() {
        when(guard.tryAcquire(any())).thenReturn(false);
        assertThatThrownBy(() -> gateway("live").text(AiPrompt.of("test", null, "s", "u")))
                .satisfies(e -> assertThat(((AiException) e).code()).isEqualTo("ai_rate_limited"));
        assertThat(client.received).isEmpty();
    }

    @Test
    void mockProfileIsNotBudgetChecked() {
        when(guard.budgetExhausted()).thenReturn(true);
        client.outputs.add("ok");
        assertThat(gateway("mock").text(AiPrompt.of("test", null, "s", "u")).value()).isEqualTo("ok");
        verify(guard, never()).budgetExhausted();
    }

    @Test
    void providerFailureIsRecordedAndTyped() {
        client.dynamic = call -> {
            throw new IllegalStateException("connection reset");
        };
        assertThatThrownBy(() -> gateway("mock").text(AiPrompt.of("test", null, "s", "u")))
                .satisfies(e -> assertThat(((AiException) e).code()).isEqualTo("ai_provider_error"));
        ArgumentCaptor<AiCallRecorder.Attempt> captor = ArgumentCaptor.forClass(AiCallRecorder.Attempt.class);
        verify(recorder).record(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(AiCall.Status.ERROR);
    }

    @Test
    void toolArgumentsAreUnmaskedAndResultsMaskedAndTraced() {
        List<Map<String, Object>> seenArgs = new ArrayList<>();
        AiTool tool = new AiTool("lookup", "Find a worker", AiTool.NO_ARGS, args -> {
            seenArgs.add(args);
            return Map.of("worker", "Julie Perrin", "hours", 35);
        });
        List<String> toolOutputs = new ArrayList<>();
        client.dynamic = call -> {
            toolOutputs.add(call.tools().get(0).invoke().apply("{\"name\":\"[PERSON_1]\"}"));
            return "done";
        };
        AiResult<String> result = gateway("mock").withTools(AiPrompt.of("test", null, "s", "Who replaces Julie Perrin?"), List.of(tool));
        assertThat(seenArgs.get(0)).containsEntry("name", "Julie Perrin");
        assertThat(toolOutputs.get(0)).doesNotContain("Julie").contains("[PERSON_1]");
        assertThat(result.toolCalls()).hasSize(1);
        assertThat(result.toolCalls().get(0).name()).isEqualTo("lookup");
    }
}
