package com.riskcontrol.gateway.repository;

import com.riskcontrol.gateway.model.RiskScoreResponse;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.function.BiFunction;

@Repository
public class RiskAuditRepository {

    private final R2dbcEntityTemplate r2dbcEntityTemplate;

    public RiskAuditRepository(R2dbcEntityTemplate r2dbcEntityTemplate) {
        this.r2dbcEntityTemplate = r2dbcEntityTemplate;
    }

    public Mono<Long> saveAudit(RiskScoreResponse response, String requestBody) {
        String sql = """
            INSERT INTO risk_request_audit (
                request_id, event_id, subject_type, subject_id,
                fraud_score, decision, reason, model_version,
                processing_time_ms, cached, request_body, created_at
            ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12)
            RETURNING id
            """;

        return r2dbcEntityTemplate.getDatabaseClient().sql(sql)
                .bind("$1", response.getRequestId())
                .bind("$2", response.getEventId())
                .bind("$3", response.getSubjectType())
                .bind("$4", response.getSubjectId())
                .bind("$5", response.getFraudScore())
                .bind("$6", response.getDecision())
                .bind("$7", response.getReason())
                .bind("$8", response.getModelVersion())
                .bind("$9", response.getProcessingTimeMs())
                .bind("$10", response.getCached())
                .bind("$11", requestBody)
                .bind("$12", Instant.now())
                .map((row, rowMetadata) -> row.get("id", Long.class))
                .first();
    }
}
