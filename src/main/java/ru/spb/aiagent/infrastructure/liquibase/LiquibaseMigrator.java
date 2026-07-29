package ru.spb.aiagent.infrastructure.liquibase;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import java.sql.Connection;
import java.sql.DriverManager;

/**
 * Bootstrap-компонент Liquibase. Запускается до старта event loop serving и HTTP-сервера.
 */
public class LiquibaseMigrator {
    /**
     * Применяет changelog к PostgreSQL через JDBC и падает fail-fast при ошибке.
     */
    public void migrate(String jdbcUrl, String user, String password) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, user, password)) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase("changelog/changelog.xml", new ClassLoaderResourceAccessor(), database)) {
                liquibase.update(new Contexts(), new LabelExpression());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to apply Liquibase migrations", e);
        }
    }
}
