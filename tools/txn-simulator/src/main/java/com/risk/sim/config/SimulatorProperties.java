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
    private Mode mode;
    private RateControl rateControl = new RateControl();
    private TimeReplay timeReplay = new TimeReplay();
    private Scenario scenario = new Scenario();
    private Execution execution = new Execution();
    private ColdStart coldStart = new ColdStart();
    private Output output = new Output();
    private Metrics metrics = new Metrics();


    public enum Mode {
        COMMON,
        FIXED_QPS,
        REPLAY_DT,
        SCENARIO,
        STREAMING
    }

    /*
     CSV Data Source Configuration
     */
    @Data
    public static class Csv {
        private String transactionPath;
        private String identityPath;
        private String encoding;
        private double dataSampleRate = 1.0;  // 0.0-1.0, 1.0 means read all data
    }

    /* Target Service Configuration */
    @Data
    public static class Target {
        private String baseUrl;
        private String endpoint;
        private int timeoutMs;
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
        private int qps;
        private int concurrency;
        private int maxInFlight;
    }

    @Data
    public static class TimeReplay {
        private double speedMultiplier;
        private boolean preserveOrder = false;  // If true, execute synchronously to preserve exact order
    }

    @Data
    public static class Scenario {
        private ScenarioType type;
        private double coldStartRatio;
        private List<String> productCds;
        private List<String> missingFeatures;
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
        private long maxRecords;
        private int startIndex;
        private int batchSize;
    }

    @Data
    public static class ColdStart {
        private boolean enabled;
        private double ratio;
        private List<String> userKeyFields;
    }

    @Data
    public static class Output {
        private String path;
        private String format;
        private boolean includeRequest;
        private boolean includeResponse;
        private boolean includeLatency;
        private List<String> outputFields;
    }

    @Data
    public static class Metrics {
        private boolean enabled;
        private String exportPercentiles;
        private int histogramPrecision;
    }
}
