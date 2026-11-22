# 异步数据采集与训练数据导出架构设计

## 1. 总体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        Gateway Service                          │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ 1. 请求拦截 (RequestInterceptor)                           │  │
│  │    - 捕获原始请求数据                                       │  │
│  │    - 生成请求快照                                           │  │
│  └───────────────────────────────────────────────────────────┘  │
│                          ↓                                      │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ 2. 风险评估 (GatewayService)                               │  │
│  │    - 调用 Orchestrator                                     │  │
│  │    - 同步获取决策结果                                       │  │
│  └───────────────────────────────────────────────────────────┘  │
│                          ↓                                      │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ 3. 响应拦截 (ResponseInterceptor)                          │  │
│  │    - 捕获响应数据（包含决策和风险评分）                      │  │
│  │    - 组装完整交易数据                                       │  │
│  │    - 异步发送到 Kafka (不阻塞返回)                          │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              ↓
                    ┌─────────────────┐
                    │   Kafka Topics  │
                    │ - tx.events     │
                    │ - tx.dlq        │
                    └─────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                     Transaction Service                          │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ 1. Kafka 消费者 (TransactionEventConsumer)                 │  │
│  │    - 订阅 tx.events 主题                                   │  │
│  │    - 批量拉取消息                                           │  │
│  │    - 失败重试机制                                           │  │
│  └───────────────────────────────────────────────────────────┘  │
│                          ↓                                      │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ 2. 批量写入 (BatchTransactionWriter)                       │  │
│  │    - 累积到批量大小或时间窗口                               │  │
│  │    - 批量插入数据库                                         │  │
│  │    - 失败记录日志                                           │  │
│  └───────────────────────────────────────────────────────────┘  │
│                          ↓                                      │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ 3. 告警监控 (DataCollectionMonitor)                        │  │
│  │    - 监控消费延迟                                           │  │
│  │    - 监控失败率                                             │  │
│  │    - 发送告警通知                                           │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                训练数据导出功能 (Transaction Service)            │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ TrainingDataExportController                              │  │
│  │    POST /api/v1/training-data/export                      │  │
│  │    - 按条件查询（时间范围、决策类型、用户）                 │  │
│  │    - 特征工程处理                                          │  │
│  │    - 导出到 CSV/Parquet/JSON                              │  │
│  │    - 支持异步任务，生成下载链接                             │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

## 2. 数据流程

### 2.1 请求采集流程

```
1. 用户请求 → Gateway
2. RequestInterceptor 捕获：
   - transactionId, userId, amount
   - 所有请求参数
   - 请求时间戳
   → 保存到 ThreadLocal

3. 风险评估 → 同步处理

4. ResponseInterceptor 捕获：
   - 决策结果 (decision)
   - 风险评分 (riskScore)
   - 响应时间
   → 从 ThreadLocal 取出请求数据
   → 组装完整 TransactionEvent
   → 异步发送到 Kafka (发送后立即返回)
```

### 2.2 异步写入流程

```
1. TransactionEventConsumer 订阅 Kafka
2. 配置：
   - batch.size: 100
   - linger.ms: 1000 (1秒)
   - max.poll.records: 500
   - enable.auto.commit: false (手动提交)

3. 批量写入：
   - 累积消息到 batch.buffer
   - 达到批量大小 或 超过时间窗口 → 触发写入
   - 使用 JPA saveAll() 批量插入

4. 失败处理：
   - 单条失败 → 记录日志，继续处理其他
   - 整批失败 → 发送到死信队列 (tx.dlq)
   - 超过重试次数 → 告警通知
```

## 3. Kafka Topic 设计

### 3.1 主事件 Topic: `tx.events`

```json
{
  "eventId": "uuid",
  "transactionId": "string",
  "eventType": "TRANSACTION_CREATED",
  "timestamp": 1737000000000,
  "payload": {
    "transactionId": "TX123456",
    "userId": "user123",
    "merchantId": "merchant001",
    "eventTimestamp": 1737000000000,
    "amount": 100.50,
    "productCd": "product_es",
    "decision": "APPROVE",
    "riskScore": 0.15,
    "isFraud": false,
    "attributes": {
      "card1": "visa",
      "card2": "credit",
      ...
    },
    "identity": {
      "id_01": "value",
      "id_02": "value",
      ...
    }
  },
  "metadata": {
    "gatewayTimestamp": 1737000000000,
    "responseTime": 150,
    "sourceIp": "192.168.1.100"
  }
}
```

**配置：**
- Partitions: 3 (可扩展)
- Replication Factor: 2
- Retention: 7 days
- Cleanup Policy: delete

### 3.2 死信队列 Topic: `tx.dlq`

**用于存储处理失败的事件，支持人工介入和数据恢复**

**配置：**
- Partitions: 1
- Replication Factor: 2
- Retention: 30 days

## 4. 组件设计

### 4.1 Gateway Service 新增组件

#### 4.1.1 RequestInterceptor

```java
@Component
public class RequestInterceptor implements HandlerInterceptor {

    private ThreadLocal<TransactionSnapshot> requestSnapshot = new ThreadLocal<>();

    @Override
    public boolean preHandle(HttpServletRequest request, ...) {
        // 1. 读取请求体
        // 2. 解析 TransactionRequest
        // 3. 创建快照
        // 4. 保存到 ThreadLocal
        return true;
    }
}
```

#### 4.1.2 ResponseInterceptor

```java
@Component
public class ResponseInterceptor implements HandlerInterceptor {

    @Autowired
    private TransactionEventPublisher eventPublisher;

    @Override
    public void afterCompletion(...) {
        // 1. 从 ThreadLocal 获取请求快照
        // 2. 组装完整 TransactionEvent
        // 3. 异步发送到 Kafka (fire-and-forget)
        // 4. 清理 ThreadLocal
    }
}
```

#### 4.1.3 TransactionEventPublisher

```java
@Component
public class TransactionEventPublisher {

    @Autowired
    private KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    public void publishTransaction(TransactionEvent event) {
        try {
            kafkaTemplate.send("tx.events", event.getTransactionId(), event)
                .addSuccessCallback(...)
                .addFailureCallback((ex) -> log.error("Failed to send event", ex));
        } catch (Exception e) {
            log.error("Publish error", e);
            // 丢日志，不影响主流程
        }
    }
}
```

### 4.2 Transaction Service 新增组件

#### 4.2.1 TransactionEventConsumer

```java
@Component
@Slf4j
public class TransactionEventConsumer {

    @Autowired
    private BatchTransactionWriter batchWriter;

    @Autowired
    private DataCollectionMonitor monitor;

    @KafkaListener(
        topics = "tx.events",
        groupId = "transaction-service-group",
        concurrency = "3"
    )
    public void consumeTransactionEvents(
            List<ConsumerRecord<String, TransactionEvent>> records) {

        List<TransactionEvent> batch = new ArrayList<>();
        for (ConsumerRecord<String, TransactionEvent> record : records) {
            batch.add(record.value());
        }

        try {
            batchWriter.writeBatch(batch);
            // 手动提交 offset
            ack.acknowledge();
            monitor.recordSuccess(batch.size());
        } catch (Exception e) {
            monitor.recordFailure(batch.size(), e);
            // 发送到死信队列
            sendToDeadLetterQueue(batch);
        }
    }
}
```

#### 4.2.2 BatchTransactionWriter

```java
@Component
public class BatchTransactionWriter {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TransactionAttributeRepository attributeRepository;

    @Autowired
    private TransactionIdentityRepository identityRepository;

    private final List<TransactionEvent> buffer = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler;

    @PostConstruct
    public void init() {
        // 定时刷新：每1秒或buffer达到100条
        scheduler.scheduleAtFixedRate(this::flush, 1, 1, TimeUnit.SECONDS);
    }

    public void writeBatch(List<TransactionEvent> events) {
        buffer.addAll(events);

        if (buffer.size() >= 100) {
            flush();
        }
    }

    @Transactional
    public synchronized void flush() {
        if (buffer.isEmpty()) return;

        try {
            List<TransactionEntity> entities = convertToEntities(buffer);
            transactionRepository.saveAll(entities);
            log.info("Batch write completed: {} records", buffer.size());
            buffer.clear();
        } catch (Exception e) {
            log.error("Batch write failed", e);
            throw e;
        }
    }
}
```

#### 4.2.3 DataCollectionMonitor

```java
@Component
public class DataCollectionMonitor {

    private AtomicLong successCount = new AtomicLong(0);
    private AtomicLong failureCount = new AtomicLong(0);
    private long lastAlertTime = 0;

    public void recordSuccess(int count) {
        successCount.addAndGet(count);
    }

    public void recordFailure(int count, Exception e) {
        long failures = failureCount.addAndGet(count);
        double failureRate = (double) failures / (successCount.get() + failures);

        // 失败率超过 5% 发送告警
        if (failureRate > 0.05 && System.currentTimeMillis() - lastAlertTime > 300000) {
            sendAlert("Data collection failure rate: " + failureRate);
            lastAlertTime = System.currentTimeMillis();
        }

        log.error("Event processing failed: count={}, error={}", count, e.getMessage());
    }

    private void sendAlert(String message) {
        // TODO: 集成告警系统（邮件/钉钉/企业微信）
        log.warn("ALERT: {}", message);
    }
}
```

#### 4.2.4 TrainingDataExportController

```java
@RestController
@RequestMapping("/api/v1/training-data")
public class TrainingDataExportController {

    @Autowired
    private TrainingDataExporter exporter;

    @PostMapping("/export")
    public ResponseEntity<ApiResponse<ExportTaskResponse>> exportTrainingData(
            @Valid @RequestBody ExportRequest request) {

        // 1. 创建导出任务
        String taskId = UUID.randomUUID().toString();
        ExportTask task = exporter.createExportTask(taskId, request);

        // 2. 异步执行导出
        CompletableFuture.runAsync(() -> exporter.executeExport(task));

        // 3. 返回任务ID
        return ResponseEntity.ok(ApiResponse.success(ExportTaskResponse.builder()
            .taskId(taskId)
            .status("PROCESSING")
            .build()));
    }

    @GetMapping("/export/{taskId}")
    public ResponseEntity<ApiResponse<ExportTaskStatus>> getExportStatus(
            @PathVariable String taskId) {
        ExportTaskStatus status = exporter.getTaskStatus(taskId);
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    @GetMapping("/download/{taskId}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String taskId) {
        // 返回生成的文件
        File file = exporter.getExportFile(taskId);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + file.getName() + "\"")
            .body(new FileSystemResource(file));
    }
}
```

#### 4.2.5 TrainingDataExporter

```java
@Service
public class TrainingDataExporter {

    @Autowired
    private TransactionRepository transactionRepository;

    private final Map<String, ExportTask> tasks = new ConcurrentHashMap<>();

    public ExportTask createExportTask(String taskId, ExportRequest request) {
        ExportTask task = ExportTask.builder()
            .taskId(taskId)
            .status(ExportStatus.PROCESSING)
            .request(request)
            .createdAt(Instant.now())
            .build();

        tasks.put(taskId, task);
        return task;
    }

    public void executeExport(ExportTask task) {
        try {
            // 1. 查询数据
            List<TransactionEntity> transactions = queryTransactions(task.getRequest());

            // 2. 特征工程
            List<TrainingSample> samples = extractFeatures(transactions);

            // 3. 导出到文件
            File outputFile = generateFile(samples, task.getRequest());

            // 4. 更新任务状态
            task.setStatus(ExportStatus.COMPLETED);
            task.setFilePath(outputFile.getAbsolutePath());
            task.setFileSize(outputFile.length());

        } catch (Exception e) {
            task.setStatus(ExportStatus.FAILED);
            task.setError(e.getMessage());
        }
    }

    private List<TrainingSample> extractFeatures(List<TransactionEntity> transactions) {
        return transactions.stream()
            .map(tx -> TrainingSample.builder()
                .label(tx.getIsFraud() ? 1 : 0)
                .features(extractFeatureMap(tx))
                .build())
            .collect(Collectors.toList());
    }

    private File generateFile(List<TrainingSample> samples, ExportRequest request) {
        String format = request.getFormat(); // CSV, PARQUET, JSON

        if ("CSV".equals(format)) {
            return generateCsv(samples);
        } else if ("PARQUET".equals(format)) {
            return generateParquet(samples);
        } else {
            return generateJson(samples);
        }
    }
}
```

## 5. 配置参数

### 5.1 Gateway Service 配置

```yaml
# application.yml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: com.risk.gateway.serializer.TransactionEventSerializer
      acks: 1  # 异步发送，不等待所有确认
      retries: 0  # 失败不重试，直接丢日志
      max.in.flight.requests.per.connection: 5
      linger.ms: 10  # 10ms批处理
      buffer.memory: 33554432  # 32MB

gateway:
  data-collection:
    enabled: true
    async-publish: true  # 异步发送，不阻塞主流程
```

### 5.2 Transaction Service 配置

```yaml
# application.yml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: transaction-service-group
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: com.risk.txn.deserializer.TransactionEventDeserializer
      auto-offset-reset: latest
      enable.auto.commit: false  # 手动提交
      max.poll.records: 500
      fetch.min.bytes: 1024
      fetch.max.wait.ms: 500

transaction:
  batch:
    size: 100  # 批量大小
    linger-ms: 1000  # 时间窗口
    enabled: true

  export:
    output-dir: ./exports
    max-file-size: 1GB
    supported-formats: CSV,PARQUET,JSON
```

## 6. 依赖添加

### 6.1 Gateway Service 需要添加

```xml
<!-- Kafka -->
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>

<!-- JSON Serialization -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

### 6.2 Transaction Service 需要添加

```xml
<!-- Kafka -->
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>

<!-- Parquet Support (可选) -->
<dependency>
    <groupId>org.apache.parquet</groupId>
    <artifactId>parquet-avro</artifactId>
    <version>1.13.1</version>
</dependency>

<!-- Apache Commons CSV -->
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-csv</artifactId>
    <version>1.10.0</version>
</dependency>
```

## 7. 性能指标

### 7.1 Gateway 层

- 请求拦截开销: < 1ms
- Kafka 发送延迟: < 5ms (异步)
- 对主流程影响: 可忽略

### 7.2 Transaction 层

- 批量写入吞吐: > 10000 TPS
- 消费延迟: < 1s
- 数据库连接池: HikariCP (默认配置)

## 8. 监控与告警

### 8.1 关键指标

- Kafka 消息积压 (Consumer Lag)
- 批量写入成功率
- 消费处理延迟 (P50, P99)
- 死信队列消息数量

### 8.2 告警规则

- 失败率 > 5% → 告警
- 消费延迟 > 30s → 告警
- 死信队列消息 > 100 → 告警
- 批量写入连续失败 3 次 → 告警

## 9. 数据导出 API

### 9.1 创建导出任务

```http
POST /api/v1/training-data/export
Content-Type: application/json

{
  "startTime": "2024-01-01T00:00:00Z",
  "endTime": "2024-01-31T23:59:59Z",
  "decisionTypes": ["APPROVE", "REJECT"],
  "format": "CSV",
  "features": ["card1", "card2", "amount", "productCd"],
  "sampleSize": 10000,
  "balancePositive": true
}

Response:
{
  "success": true,
  "data": {
    "taskId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "PROCESSING"
  }
}
```

### 9.2 查询任务状态

```http
GET /api/v1/training-data/export/{taskId}

Response:
{
  "success": true,
  "data": {
    "taskId": "...",
    "status": "COMPLETED",
    "progress": 100,
    "fileSize": 1048576,
    "fileName": "training_data_20240116.csv",
    "downloadUrl": "/api/v1/training-data/download/..."
  }
}
```

### 9.3 下载文件

```http
GET /api/v1/training-data/download/{taskId}

Response: CSV/Parquet/JSON File
```

## 10. 实施计划

1. ✅ 配置 Kafka (docker-compose 已有)
2. ⬜ 添加 Kafka 依赖到两个服务
3. ⬜ 实现 Gateway 层拦截器
4. ⬜ 实现 Transaction 层消费者
5. ⬜ 实现批量写入
6. ⬜ 实现告警监控
7. ⬜ 实现数据导出功能
8. ⬜ 集成测试
9. ⬜ 性能测试

## 11. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| Kafka 故障导致数据丢失 | 高 | 死信队列 + 日志记录，定期检查 |
| 批量写入性能瓶颈 | 中 | 调整批量大小和时间窗口 |
| 消费者积压 | 中 | 增加消费者并发数 |
| 数据库连接耗尽 | 高 | 使用独立连接池，限流 |
| 导出文件过大 | 低 | 支持分片导出，限制文件大小 |
