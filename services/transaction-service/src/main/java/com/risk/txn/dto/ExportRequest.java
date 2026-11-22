package com.risk.txn.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Request DTO for training data export.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportRequest {

    @JsonProperty("startTime")
    @NotNull(message = "Start time is required")
    private Instant startTime;

    @JsonProperty("endTime")
    @NotNull(message = "End time is required")
    private Instant endTime;

    @JsonProperty("decisionTypes")
    private List<String> decisionTypes; // APPROVE, REVIEW, REJECT

    @JsonProperty("format")
    @NotBlank(message = "Format is required")
    private String format = "CSV"; // CSV, PARQUET, JSON

    @JsonProperty("features")
    private List<String> features; // Specific features to export

    @JsonProperty("sampleSize")
    private Integer sampleSize; // Limit number of samples

    @JsonProperty("balancePositive")
    private Boolean balancePositive = false; // Balance positive/negative samples

    @JsonProperty("includeFraudLabel")
    private Boolean includeFraudLabel = true;

    @JsonProperty("outputFileName")
    private String outputFileName; // Custom output file name
}
