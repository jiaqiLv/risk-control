package com.riskcontrol.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;

import java.time.Duration;

@Configuration
public class DatabaseConfig {

    @Value("${spring.r2dbc.host:localhost}")
    private String dbHost;

    @Value("${spring.r2dbc.port:5432}")
    private int dbPort;

    @Value("${spring.r2dbc.database:risk_control}")
    private String dbName;

    @Value("${spring.r2dbc.username:postgres}")
    private String dbUser;

    @Value("${spring.r2dbc.password:postgres}")
    private String dbPassword;

    @Bean
    public PostgresqlConnectionFactory connectionFactory() {
        PostgresqlConnectionConfiguration config = PostgresqlConnectionConfiguration.builder()
                .host(dbHost)
                .port(dbPort)
                .database(dbName)
                .username(dbUser)
                .password(dbPassword)
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        return new PostgresqlConnectionFactory(config);
    }

    @Bean
    public R2dbcEntityTemplate r2dbcEntityTemplate(PostgresqlConnectionFactory connectionFactory) {
        return new R2dbcEntityTemplate(connectionFactory);
    }
}
