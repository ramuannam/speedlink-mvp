package com.speedlink.app.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;

@Configuration
public class DatabaseCompatibilityConfig {
    private static final Logger log = LoggerFactory.getLogger(DatabaseCompatibilityConfig.class);

    @Bean
    ApplicationRunner applyLegacyAuthSchemaCompatibility(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                if (jdbcTemplate.getDataSource() == null) {
                    return;
                }
                String databaseName;
                try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
                    databaseName = connection.getMetaData().getDatabaseProductName().toLowerCase();
                }
                if (!databaseName.contains("postgres")) {
                    return;
                }

                jdbcTemplate.execute("alter table user_accounts drop column if exists password_hash");
                log.info("Dropped legacy password_hash column if it existed.");
                jdbcTemplate.execute("""
                        create unique index if not exists user_accounts_phone_unique
                        on user_accounts (phone)
                        where phone is not null and phone <> ''
                        """);
                log.info("Ensured unique user account phone index exists.");
            } catch (Exception exception) {
                log.warn("Could not apply legacy auth schema compatibility changes", exception);
            }
        };
    }
}
