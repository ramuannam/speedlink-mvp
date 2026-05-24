package com.speedlink.app.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.util.List;

@Configuration
public class DatabaseCompatibilityConfig {
    private static final Logger log = LoggerFactory.getLogger(DatabaseCompatibilityConfig.class);

    @Bean
    ApplicationRunner removeLegacyPhoneUniqueConstraint(JdbcTemplate jdbcTemplate) {
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

                List<String> constraintNames = jdbcTemplate.queryForList("""
                        select c.conname
                        from pg_constraint c
                        join pg_class t on c.conrelid = t.oid
                        join pg_attribute a on a.attrelid = t.oid and a.attnum = any(c.conkey)
                        where t.relname = 'user_accounts'
                          and c.contype = 'u'
                          and a.attname = 'phone'
                        """, String.class);

                for (String constraintName : constraintNames) {
                    String quotedConstraint = constraintName.replace("\"", "\"\"");
                    jdbcTemplate.execute("alter table user_accounts drop constraint if exists \"" + quotedConstraint + "\"");
                    log.info("Dropped legacy unique phone constraint: {}", constraintName);
                }
            } catch (Exception exception) {
                log.warn("Could not inspect or drop legacy phone uniqueness constraint", exception);
            }
        };
    }
}
