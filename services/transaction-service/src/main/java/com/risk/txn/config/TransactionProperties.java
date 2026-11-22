package com.risk.txn.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for transaction service.
 */
@Data
@Component
@ConfigurationProperties(prefix = "transaction")
public class TransactionProperties {

    private BatchConfig batch = new BatchConfig();
    private ExportConfig export = new ExportConfig();

    @Data
    public static class BatchConfig {
        private int size = 100;
        private long lingerMs = 1000;
        private boolean enabled = true;
    }

    @Data
    public static class ExportConfig {
        private String outputDir = "./exports";
        private String maxFileSize = "1GB";
        private String supportedFormats = "CSV,PARQUET,JSON";
    }
}
