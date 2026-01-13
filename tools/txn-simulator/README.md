# Transaction Simulator

A transaction simulation tool for fraud detection systems using the IEEE-CIS dataset.

## Overview

This simulator reads transaction data from IEEE-CIS CSV files and replays it against your fraud detection services. It supports multiple replay modes, collects performance metrics, and generates evaluation reports.

## Features

- **Multiple Data Sources**: Reads from `train_transaction.csv` and `train_identity.csv`
- **Three Replay Modes**:
  - **FIXED_QPS**: Constant rate replay with configurable QPS
  - **REPLAY_DT**: Time-based replay with acceleration
  - **SCENARIO**: Scenario-based testing (fraud-only, cold start, etc.)
- **Cold Start Simulation**: Simulates new users not in the database
- **Multiple Targets**: Gateway, Transaction Service, or direct Python inference
- **Comprehensive Metrics**: Latency, throughput, accuracy, precision, recall, etc.
- **Flexible Output**: CSV, JSONL, or JSON formats

## Configuration

Edit `src/main/resources/application.yml` to configure the simulator:

```yaml
simulator:
  csv:
    transaction-path: "data/ieee-cis/train_transaction.csv"
    identity-path: "data/ieee-cis/train_identity.csv"

  target:
    base-url: "http://localhost:8080"
    endpoint: "/api/v1/transactions"
    type: GATEWAY  # GATEWAY, TRANSACTION_SERVICE, RISK_ORCHESTRATOR, PYTHON_INFERENCE

  mode: FIXED_QPS  # FIXED_QPS, REPLAY_DT, SCENARIO

  rate-control:
    qps: 200
    concurrency: 32
    max-in-flight: 100

  cold-start:
    enabled: true
    ratio: 0.05  # 5% of transactions will be cold start

  output:
    path: "output/simulation-results"
    format: "JSONL"
```

## Building

```bash
cd tools/txn-simulator
mvn clean package
```

## Running

```bash
java -jar target/txn-simulator-1.0-SNAPSHOT.jar
```

Or with Maven:

```bash
mvn spring-boot:run
```

## Usage Examples

### 1. Fixed QPS Mode (Default)

Replay transactions at a constant rate of 200 QPS:

```yaml
mode: FIXED_QPS
rate-control:
  qps: 200
```

### 2. Time-Based Replay Mode

Replay transactions based on their original timing, accelerated 60x:

```yaml
mode: REPLAY_DT
time-replay:
  speed-multiplier: 60.0
```

### 3. Scenario Testing - Fraud Only

Test only fraud transactions:

```yaml
mode: SCENARIO
scenario:
  type: FRAUD_ONLY
```

### 4. Cold Start Testing

Test with 10% new users:

```yaml
cold-start:
  enabled: true
  ratio: 0.10
```

## Project Structure

```
com.risk.tools.sim/
├── SimulatorApplication.java    # Main entry point
├── config/
│   ├── SimulatorProperties.java # Configuration properties
│   └── WebClientConfig.java      # WebClient configuration
├── source/
│   ├── TransactionRecord.java   # Transaction data model
│   ├── CsvTransactionReader.java # Transaction CSV reader
│   ├── CsvIdentityReader.java    # Identity CSV reader
│   ├── Joiner.java               # Data joiner
│   └── RecordSampler.java        # Sampling and filtering
├── mapping/
│   ├── RequestMapper.java        # Request mapping
│   └── FeatureEncoder.java       # Feature encoding
├── client/
│   ├── HttpClientSender.java     # HTTP client
│   ├── GrpcClientSender.java     # gRPC client
│   └── SimulationResponse.java   # Response model
├── runner/
│   ├── ReplayEngine.java         # Replay engine
│   └── RateLimiter.java          # Rate limiter
├── metrics/
│   ├── LatencyRecorder.java      # Latency metrics
│   └── ResultSink.java           # Result output
└── eval/
    ├── OfflineEvaluator.java     # Offline evaluation
    └── EvaluationReport.java     # Evaluation report
```

## Output

The simulator generates:

1. **Results File**: `simulation-results_YYYYMMDD_HHmmss.{csv|jsonl}`
   - Contains all transaction results with predictions

2. **Console Output**: Real-time progress and statistics

3. **Metrics**:
   - Latency statistics (P50, P95, P99)
   - Throughput and success rate
   - Confusion matrix
   - Accuracy, Precision, Recall, F1

## Cold Start Simulation

The simulator can create "cold start" scenarios where the user doesn't exist in the database:

```yaml
cold-start:
  enabled: true
  ratio: 0.05  # 5% of transactions
  user-key-fields:
    - card1
    - addr1
    - DeviceInfo
```

This tests your system's behavior when:
- User embeddings are missing
- Historical features are unavailable
- Graph nodes don't exist

## Evaluation Metrics

The simulator calculates:

- **Accuracy**: Overall correctness
- **Precision**: True positives / (True positives + False positives)
- **Recall**: True positives / (True positives + False negatives)
- **F1 Score**: Harmonic mean of precision and recall
- **AUC**: Area under ROC curve (optional)
- **KS**: Kolmogorov-Smirnov statistic (optional)

## Tips

1. **Start Small**: Use `execution.max-records` to test with a subset first
2. **Adjust QPS**: Lower QPS if you see timeouts
3. **Monitor Resources**: Check CPU/memory on both client and server
4. **Check Logs**: Review server logs for errors during high load
5. **Use Scenarios**: Focus on specific test cases (fraud-only, high missing rate)

## Troubleshooting

### High Error Rate

- Lower QPS: `rate-control.qps: 100`
- Increase timeout: `target.timeout-ms: 10000`
- Check server logs for errors

### Out of Memory

- Reduce concurrency: `rate-control.concurrency: 16`
- Reduce max-in-flight: `rate-control.max-in-flight: 50`
- Process in batches: `execution.max-records: 10000`

### Slow Performance

- Use HTTP client (faster than gRPC for simple cases)
- Disable detailed logging: `logging.level.com.risk.tools.sim: INFO`
- Increase batch size for output

## License

This is part of the Risk Control System project.
