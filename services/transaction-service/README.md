# Transaction Service

风险控制系统的交易存储与查询服务，负责交易数据的持久化、历史查询和统计功能。

## 功能特性

- ✅ 交易记录存储（同步写入）
- ✅ 交易详情查询
- ✅ 用户历史交易查询
- ✅ 用户统计摘要（总交易数、金额、决策分布等）
- ✅ 时间窗口统计（24h/7d/30d）
- ✅ 用户时间序列数据
- ✅ 实时统计特征
- ✅ Liquibase 自动建表
- ✅ 数据库连接池配置
- ✅ 完善的错误处理和日志

## 技术栈

- **Java 17**
- **Spring Boot 3.x**
- **Spring Data JPA** - 数据访问层
- **PostgreSQL** - 关系型数据库
- **Liquibase** - 数据库版本管理
- **Lombok** - 减少样板代码
- **Validation** - 参数校验

## 项目结构

```
transaction-service/
├── src/main/java/com/risk/txn/
│   ├── TransactionApplication.java        # 主应用类
│   ├── config/
│   │   └── WebConfig.java                 # Web配置（跨域等）
│   ├── controller/
│   │   └── TransactionController.java     # REST控制器
│   ├── service/
│   │   └── TransactionService.java        # 业务逻辑层
│   ├── dto/
│   │   ├── TransactionCreateRequest.java  # 交易创建请求
│   │   ├── TransactionResponse.java       # 交易响应
│   │   ├── UserHistoryResponse.java       # 用户历史响应
│   │   ├── UserStatsSummary.java          # 用户统计摘要
│   │   ├── TimeWindowStats.java           # 时间窗口统计
│   │   ├── TimeSeriesData.java            # 时间序列数据
│   │   └── ApiResponse.java               # 通用响应包装
│   └── exception/
│       └── GlobalExceptionHandler.java    # 全局异常处理
└── src/main/resources/
    └── application.yml                     # 配置文件
```

## 配置说明

### application.yml

```yaml
server:
  port: 8082  # 服务端口

spring:
  application:
    name: transaction-service

  # 导入 data-access 模块的数据库配置
  config:
    import: classpath:application-data-access.yml

  # JPA 配置
  jpa:
    hibernate:
      ddl-auto: validate  # 使用 Liquibase 管理 schema
    show-sql: true

  # Liquibase 配置
  liquibase:
    enabled: true
    change-log: classpath:db/changelog/master.yaml
    drop-first: false  # 生产环境必须为 false
```

### 数据库配置

数据库配置在 `libs/data-access/src/main/resources/application-data-access.yml` 中：

```yaml
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/riskcontrol
    username: postgres
    password: postgres

  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
```

## API 接口

### 1. 创建交易记录

**POST** `/api/v1/transactions`

请求体示例：
```json
{
  "transactionId": "txn_20250112_001",
  "userId": "user_12345",
  "eventTimestamp": 1736640000000,
  "amount": 1500.00,
  "currency": "USD",
  "productCd": "W",
  "channel": "online",
  "merchantId": "merchant_456",
  "deviceId": "device_abc",
  "ipAddress": "192.168.1.1",
  "attributes": {
    "card1": 12345,
    "addr1": 100
  },
  "decision": "APPROVE",
  "riskScore": 0.25,
  "isFraud": false
}
```

响应示例：
```json
{
  "success": true,
  "data": {
    "transactionId": "txn_20250112_001",
    "userId": "user_12345",
    "eventTimestamp": 1736640000000,
    "amount": 1500.00,
    "currency": "USD",
    "productCd": "W",
    "channel": "online",
    "merchantId": "merchant_456",
    "decision": "APPROVE",
    "riskScore": 0.25,
    "isFraud": false,
    "processedAt": 1736640001500,
    "createdAt": "2026-01-12T10:00:01",
    "updatedAt": "2026-01-12T10:00:01"
  },
  "timestamp": 1736640001500
}
```

### 2. 查询交易详情

**GET** `/api/v1/transactions/{transactionId}`

响应示例：同上

### 3. 查询用户历史交易

**GET** `/api/v1/users/{userId}/history`

查询参数：
- `startTime`: 开始时间戳（可选）
- `endTime`: 结束时间戳（可选）
- `limit`: 返回数量限制（可选，默认 100）

响应示例：
```json
{
  "success": true,
  "data": {
    "userId": "user_12345",
    "summary": {
      "totalTxns": 150,
      "totalAmount": 85000.00,
      "avgAmount": 566.67,
      "approvedTxns": 140,
      "reviewedTxns": 8,
      "rejectedTxns": 2,
      "approvalRate": 0.933
    },
    "timeWindowStats": {
      "last24h": {
        "count": 5,
        "totalAmount": 2500.00,
        "avgAmount": 500.00
      },
      "last7d": {
        "count": 25,
        "totalAmount": 12000.00,
        "avgAmount": 480.00
      },
      "last30d": {
        "count": 90,
        "totalAmount": 45000.00,
        "avgAmount": 500.00
      }
    },
    "transactions": [
      { /* TransactionResponse */ },
      // ...
    ]
  }
}
```

### 4. 获取用户实时统计特征

**GET** `/api/v1/users/{userId}/realtime-stats`

返回用户最近 24 小时的统计摘要，用于实时特征计算。

响应示例：
```json
{
  "success": true,
  "data": {
    "totalTxns": 5,
    "totalAmount": 2500.00,
    "avgAmount": 500.00,
    "approvedTxns": 4,
    "reviewedTxns": 1,
    "rejectedTxns": 0,
    "approvalRate": 0.8
  }
}
```

### 5. 获取用户时间序列数据

**GET** `/api/v1/users/{userId}/timeseries`

查询参数：
- `window`: 时间窗口，`1h` | `24h` | `7d` | `30d`（默认 `24h`）
- `metric`: 指标类型，`count` | `amount` | `avg_amount`（默认 `count`）

响应示例：
```json
{
  "success": true,
  "data": {
    "userId": "user_12345",
    "window": "24h",
    "metric": "count",
    "data": [
      {"timestamp": 1736640000000, "value": 5},
      {"timestamp": 1736636400000, "value": 3},
      // ...
    ]
  }
}
```

### 6. 健康检查

**GET** `/api/v1/health`

响应示例：
```json
{
  "success": true,
  "data": {
    "status": "UP",
    "service": "transaction-service",
    "timestamp": 1736640000000
  }
}
```

### 7. 服务信息

**GET** `/api/v1/info`

响应示例：
```json
{
  "success": true,
  "data": {
    "service": "transaction-service",
    "version": "1.0.0-SNAPSHOT",
    "description": "Transaction Storage and Query Service"
  }
}
```

## 快速开始

### 1. 启动 PostgreSQL 数据库

确保 PostgreSQL 数据库运行在 `localhost:5432`，数据库名为 `riskcontrol`。

使用 Docker 启动：
```bash
docker run -d \
  --name riskcontrol-postgres \
  -e POSTGRES_DB=riskcontrol \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:15
```

### 2. 编译项目

```bash
cd services/transaction-service
mvn clean compile
```

### 3. 运行服务

```bash
mvn spring-boot:run
```

或者直接打包后运行：
```bash
mvn clean package
java -jar target/transaction-service-1.0.0-SNAPSHOT.jar
```

### 4. 测试接口

使用 cURL 测试：

```bash
# 健康检查
curl http://localhost:8082/api/v1/health

# 创建交易
curl -X POST http://localhost:8082/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "txn_test_001",
    "userId": "user_test_001",
    "eventTimestamp": 1736640000000,
    "amount": 1500.00,
    "currency": "USD",
    "productCd": "W",
    "channel": "online",
    "decision": "APPROVE",
    "riskScore": 0.25
  }'

# 查询交易详情
curl http://localhost:8082/api/v1/transactions/txn_test_001

# 查询用户历史
curl http://localhost:8082/api/v1/users/user_test_001/history

# 查询用户实时统计
curl http://localhost:8082/api/v1/users/user_test_001/realtime-stats
```

## 数据库表结构

### transactions（交易主表）

表结构通过 Liquibase 自动创建，定义在 `libs/data-access/src/main/resources/db/changelog/master.yaml`。

主要字段：
- `id`: BIGSERIAL，主键（自增）
- `transaction_id`: VARCHAR(64)，交易ID（业务主键，唯一）
- `user_id`: VARCHAR(64)，用户ID
- `event_timestamp`: BIGINT，事件时间戳（毫秒）
- `amount`: NUMERIC(19,2)，交易金额
- `product_cd`: VARCHAR(10)，产品代码
- `channel`: VARCHAR(20)，交易渠道
- `decision`: VARCHAR(20)，风险决策结果（APPROVE/REVIEW/REJECT/PENDING）
- `risk_score`: DOUBLE PRECISION，风险评分（0.0-1.0）
- `is_fraud`: BOOLEAN，是否为欺诈交易
- `created_at`: TIMESTAMP，创建时间
- `updated_at`: TIMESTAMP，更新时间

索引：
- `idx_transaction_id` (transaction_id)
- `idx_user_id` (user_id)
- `idx_event_timestamp` (event_timestamp)
- `idx_decision` (decision)
- `idx_created_at` (created_at)

## 依赖关系

Transaction Service 依赖以下模块：
- **libs/data-access**: 共享数据访问层（实体类、Repository 接口）

## 与其他服务集成

### 作为 Orchestrator Service 的下游服务

Orchestrator Service 可以调用 Transaction Service 的以下接口：

1. **用户历史查询** - 用于特征提取
   ```
   GET /api/v1/users/{userId}/history
   ```

2. **用户实时统计** - 用于实时特征计算
   ```
   GET /api/v1/users/{userId}/realtime-stats
   ```

3. **创建交易记录** - 用于保存评估结果
   ```
   POST /api/v1/transactions
   ```

### 与 Gateway Service 集成

Gateway Service 可以通过 Transaction Service 查询交易详情和用户历史，用于：
- 用户风险画像展示
- 交易历史查询
- 统计数据分析

## 错误处理

所有错误响应遵循统一格式：

```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "Error message",
    "detail": "Detailed error information"
  },
  "timestamp": 1736640000000
}
```

常见错误码：
- `VALIDATION_FAILED`: 参数校验失败（400）
- `INVALID_ARGUMENT`: 非法参数（400）
- `NOT_FOUND`: 资源不存在（404）
- `INTERNAL_ERROR`: 内部错误（500）

## 日志

日志文件位置：`logs/transaction-service.log`

日志级别配置：
- `com.risk.txn`: DEBUG
- `org.springframework.web`: INFO
- `org.hibernate.SQL`: DEBUG

关键日志：
- 交易创建：`Creating transaction: transactionId=...`
- 交易查询：`Fetching transaction: transactionId=...`
- 用户历史查询：`Fetching user history: userId=...`
- 错误信息：`Failed to create transaction: ...`

## 监控

### Spring Actuator

- `GET /actuator/health` - 服务健康状态
- `GET /actuator/info` - 应用信息
- `GET /actuator/metrics` - 性能指标
- `GET /actuator/prometheus` - Prometheus 指标

### 自定义端点

- `GET /api/v1/health` - 业务健康检查
- `GET /api/v1/info` - 服务信息

## 性能优化建议

### 1. 数据库索引优化

确保以下索引已创建：
- `user_id` - 用于用户查询
- `event_timestamp` - 用于时间范围查询
- `(user_id, event_timestamp)` - 组合索引，加速用户历史查询

### 2. 分页查询

对于大量数据的查询，建议实现分页功能：
```java
Page<TransactionEntity> findByUserId(String userId, Pageable pageable);
```

### 3. 缓存策略

对于用户统计数据，可以考虑使用 Redis 缓存：
- 缓存用户统计摘要（TTL: 5分钟）
- 缓存时间窗口统计（TTL: 5分钟）
- 在交易创建时主动刷新缓存

### 4. 异步写入

对于高并发场景，可以考虑使用 R2DBC 异步写入：
```java
@Autowired
private TransactionR2dbcRepository r2dbcRepository;

public Mono<TransactionEntity> saveAsync(TransactionEntity entity) {
    return r2dbcRepository.save(entity);
}
```

## 后续开发计划

- [ ] 实现批量交易查询接口
- [ ] 实现交易图谱查询接口
- [ ] 实现交易对手方查询接口
- [ ] 添加 Redis 缓存支持
- [ ] 实现分页查询
- [ ] 添加 Kafka 事件发布
- [ ] 实现数据导出功能
- [ ] 添加 Swagger API 文档

## 故障排查

### 问题1：Liquibase 迁移失败

**症状**：启动时报错 "Liquibase failed to update database"

**解决方案**：
1. 检查数据库连接配置是否正确
2. 确保数据库已创建
3. 检查数据库用户权限
4. 查看详细错误日志

### 问题2：端口 8082 被占用

**解决方案**：
修改 `application.yml` 中的 `server.port` 为其他端口。

### 问题3：交易插入失败

**症状**：返回 "Transaction already exists"

**原因**：`transaction_id` 是唯一字段，重复插入会失败

**解决方案**：
1. 确保每次请求使用唯一的 `transaction_id`
2. 或者实现更新逻辑（先查询再决定插入或更新）

## 许可证

Copyright © 2026 Risk Control System
