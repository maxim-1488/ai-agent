package ru.spb.aiagent.infrastructure.ai;

/**
 * Результат mock AI-выполнения.
 */
public record AiExecutionResult(String text) {
    /**
     * Создаёт результат AI.
     */
    public AiExecutionResult {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("AI result is blank");
        }
    }
}
