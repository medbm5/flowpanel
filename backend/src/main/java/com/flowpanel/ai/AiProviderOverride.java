package com.flowpanel.ai;

import java.util.function.Supplier;

/**
 * Forces the deterministic mock for chat calls in the current thread, even in the live profile. Used by the demo
 * seeder so seeding is free, reproducible and never depends on the provider. Embeddings still use the active model so
 * stored vectors stay searchable.
 */
public final class AiProviderOverride {

    private static final ThreadLocal<Boolean> MOCK_CHAT = ThreadLocal.withInitial(() -> false);

    private AiProviderOverride() {
    }

    public static boolean mockChat() {
        return MOCK_CHAT.get();
    }

    public static <T> T withMockChat(Supplier<T> action) {
        boolean previous = MOCK_CHAT.get();
        MOCK_CHAT.set(true);
        try {
            return action.get();
        } finally {
            MOCK_CHAT.set(previous);
        }
    }

    public static void withMockChat(Runnable action) {
        withMockChat(() -> {
            action.run();
            return null;
        });
    }
}
