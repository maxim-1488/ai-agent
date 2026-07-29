package ru.spb.aiagent.infrastructure.ai;

/**
 * Настройки mock AI: задержка шага прогресса и общий timeout.
 */
public record MockAiOptions(int stepDelayMs, int timeoutMs) {
    /**
     * Создаёт настройки mock AI.
     */
    public MockAiOptions {
        if (stepDelayMs <= 0 || timeoutMs <= 0) {
            throw new IllegalArgumentException("AI timings must be positive");
        }
    }
}
