package ru.spb.aiagent;

import ru.spb.aiagent.infrastructure.config.AppConfig;

/**
 * Точка входа backend-приложения.
 */
public class Main {
    /**
     * Запускает bootstrap приложения.
     */
    public static void main(String[] args) {
        new ApplicationBootstrap().start(AppConfig.fromEnv());
    }
}
