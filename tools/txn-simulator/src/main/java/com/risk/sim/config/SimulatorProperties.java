package com.risk.sim.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "simulator")
public class SimulatorProperties {

    private Csv csv = new Csv();
    private Target target = new Target();
    private String mode = "FIXED_QPS";
    private RateControl rateControl = new RateControl();
    private TimeReplay timeReplay = new TimeReplay();
    private Scenario scenario = new Scenario();
    private Execution execution = new Execution();
    private ColdStart coldStart = new ColdStart();
    private Output output = new Output();
    private Metrics metrics = new Metrics();

    @Data
    public static class Csv {
        private String transactionPath = "data/ieee-cis/train_transaction.csv";
        private String identityPath = "data/ieee-cis/train_identity.csv";
        private String encoding = "UTF-8";
    }

    @Data
    public static class Target {
        private String baseUrl = "http://localhost:8080";
        private String endpoint = "/api/v1/transactions";
        private int timeoutMs = 5000;
        private TargetType type = TargetType.GATEWAY;
    }

    public enum TargetType {
        GATEWAY,
        TRANSACTION_SERVICE,
        RISK_ORCHESTRATOR,
        PYTHON_INFERENCE
    }

    @Data
    public static class RateControl {
        private int qps = 200;
        private int concurrency = 32;
        private int maxInFlight = 100;
    }

    @Data
    public static class TimeReplay {
        private double speedMultiplier = 60.0;
    }

    @Data
    public static class Scenario {
        private ScenarioType type = ScenarioType.ALL;
        private double coldStartRatio = 0.05;
        private List<String> productCds = List.of();
        private List<String> missingFeatures = List.of();
    }

    public enum ScenarioType {
        ALL,
        FRAUD_ONLY,
        LEGIT_ONLY,
        COLD_START,
        HIGH_MISSING_RATE
    }

    @Data
    public static class Execution {
        private long maxRecords = -1;
        private int startIndex = 0;
        private int batchSize = 1000;
    }

    @Data
    public static class ColdStart {
        private boolean enabled = true;
        private double ratio = 0.05;
        private List<String> userKeyFields = List.of("card1", "addr1", "DeviceInfo");
    }

    @Data
    public static class Output {
        private String path = "output/simulation-results";
        private String format = "JSONL";
        private boolean includeRequest = true;
        private boolean includeResponse = true;
        private boolean includeLatency = true;
        private List<String> outputFields = null;
        // private List<String> outputFields = List.of(
        //     "transactionId", "isFraud",
        //     "transactionAmt", "productCd", "card1", "card2", "card3", "card4", "card5", "card6",
        //     "addr1", "addr2", "dist1", "dist2",
        //     "deviceType", "deviceInfo"
        // );
    }

    @Data
    public static class Metrics {
        private boolean enabled = true;
        private String exportPercentiles = "0.5,0.75,0.9,0.95,0.99";
        private int histogramPrecision = 3;
    }
}
