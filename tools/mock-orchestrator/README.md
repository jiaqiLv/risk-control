# Mock Orchestrator - 使用指南

## 概述

Mock Orchestrator 是一个模拟的 Orchestrator 服务，用于在真实 Orchestrator 未实现时，测试 txn-simulator 与 Gateway 之间的交互。

## 架构

```
txn-simulator → Gateway (8080) → Mock Orchestrator (8081)
                      ↓
                Sentinel 限流/熔断
```

## 快速开始

### 1. 启动 Mock Orchestrator

```bash
cd tools/mock-orchestrator

# 编译
mvn clean package

# 运行
java -jar target/mock-orchestrator-1.0.0.jar
```

或者直接使用 Maven：
```bash
mvn spring-boot:run
```

### 2. 验证 Mock Orchestrator 运行

```bash
# 健康检查
curl http://localhost:8081/actuator/health

# 应该返回：
# {"status":"UP"}
```

### 3. 启动 Gateway

```bash
cd services/gateway-service
mvn spring-boot:run
```

### 4. 运行测试

```bash
cd tools/txn-simulator

# 使用测试配置运行
mvn spring-boot:run -Dspring-boot.run.arguments=--spring.config.location=application-test-gateway.yml
```

## Mock Orchestrator 功能

### 1. 交易评估端点

**接口**: `POST /api/v1/evaluate`

**请求示例**:
```json
{
  "transactionId": "txn_001",
  "userId": "user_123",
  "transactionAmt": 5000.00,
  "merchantId": "merchant_456"
}
```

**响应逻辑**:
- **金额 > $10,000**: 高风险，返回 `REVIEW`，riskScore 0.8
- **金额 > $1,000**: 中等风险，随机返回 `REVIEW` 或 `APPROVE`，riskScore 0.4-0.7
- **金额 ≤ $1,000**: 低风险，90% 返回 `APPROVE`，10% 返回 `REVIEW`，riskScore 0.0-0.3

**响应示例**:
```json
{
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "transactionId": "txn_001",
  "decision": "APPROVE",
  "riskScore": 0.25,
  "reasons": [],
  "processedAt": 1736959200000
}
```

### 2. 健康检查端点

**接口**: `GET /actuator/health`

**响应**:
```json
{
  "status": "UP"
}
```

### 3. 性能模拟

- 随机处理延迟：10-50ms
- 模拟真实的处理时间分布

## 测试场景

### 场景 1：基础功能测试

```bash
# 1. 启动 Mock Orchestrator (8081)
cd tools/mock-orchestrator
mvn spring-boot:run

# 2. 启动 Gateway (8080)
cd services/gateway-service
mvn spring-boot:run

# 3. 发送测试请求
curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "test_001",
    "userId": "user_001",
    "transactionAmt": 500.00
  }'
```

**预期结果**: 返回 `APPROVE` 决策

### 场景 2：高金额测试

```bash
curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "test_002",
    "userId": "user_001",
    "transactionAmt": 15000.00
  }'
```

**预期结果**: 返回 `REVIEW` 决策，riskScore > 0.8

### 场景 3：压力测试（验证限流）

```bash
cd tools/txn-simulator

# 运行模拟器（50 QPS，1000 个请求）
mvn spring-boot:run -Dspring-boot.run.arguments=--spring.config.location=application-test-gateway.yml
```

**验证点**:
- ✅ Gateway 限流生效（1000 QPS）
- ✅ 并发限流生效（200 threads）
- ✅ 熔断器未触发（Mock Orchestrator 正常响应）
- ✅ 延迟在可接受范围内（< 500ms）

### 场景 4：熔断降级测试

```bash
# 1. 停止 Mock Orchestrator
# Ctrl+C in Mock Orchestrator terminal

# 2. 发送测试请求
curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "test_003",
    "userId": "user_001",
    "transactionAmt": 500.00
  }'
```

**预期结果**: Gateway 返回降级响应
```json
{
  "decision": "REVIEW",
  "riskScore": 0.5,
  "reasons": ["Service unavailable"],
  "statusCode": 503
}
```

### 场景 5：慢调用熔断测试

修改 Mock Orchestrator 添加人为延迟：

```java
// 在 OrchestratorController.java 的 evaluate 方法中添加
Thread.sleep(4000); // 4秒延迟，触发慢调用熔断
```

**预期结果**:
- 前几个请求返回正常（慢响应）
- 超过 50% 慢调用后触发熔断
- 后续请求返回 HTTP 503

## 监控和日志

### Mock Orchestrator 日志

```
INFO  - Received evaluation request: transactionId=txn_001, userId=user_001, amount=5000.0
INFO  - Returning decision: transactionId=txn_001, decision=REVIEW, riskScore=0.8
```

### Gateway 日志

```
INFO  - Processing transaction: transactionId=txn_001, requestId=xxx
INFO  - Transaction processed: transactionId=txn_001, decision=REVIEW, latency=45ms
```

### Sentinel Dashboard

如果启动了 Sentinel Dashboard (8858)，可以实时查看：
- 实时 QPS
- 拒绝的请求数
- 熔断器状态
- 响应时间分布

## 故障排查

### 问题 1：Gateway 启动失败

**检查**:
```bash
# 确认端口 8081 可用（Mock Orchestrator）
curl http://localhost:8081/actuator/health

# 确认端口 8080 未被占用
netstat -ano | findstr :8080
```

### 问题 2：txn-simulator 连接失败

**检查配置** (`application-test-gateway.yml`):
```yaml
target:
  base-url: http://localhost:8080  # Gateway 地址
  endpoint: /api/v1/transactions   # Gateway 端点
```

### 问题 3：所有请求返回 REVIEW

**原因**: Mock Orchestrator 未启动或 Gateway 无法连接

**解决**:
```bash
# 检查 Mock Orchestrator 健康状态
curl http://localhost:8081/actuator/health

# 检查 Gateway 日志
tail -f services/gateway-service/logs/gateway-service.log
```

## 配置选项

### Mock Orchestrator 配置 (`application.yml`)

```yaml
server:
  port: 8081  # 修改端口（默认 8081）

logging:
  level:
    com.risk.mock: DEBUG  # 调整日志级别
```

### Gateway 配置 (`application.yml`)

```yaml
gateway:
  orchestrator-base-url: http://localhost:8081  # Mock Orchestrator 地址
  timeout-ms: 5000                             # 超时时间
```

## 下一步

测试完成后，当真实 Orchestrator 实现时：

1. 停止 Mock Orchestrator
2. 启动真实 Orchestrator (8081)
3. 无需修改 Gateway 配置
4. 运行相同的测试验证

## 总结

Mock Orchestrator 提供了：
- ✅ 快速验证 Gateway 功能
- ✅ 测试限流和熔断机制
- ✅ 模拟各种响应场景
- ✅ 无需依赖真实的 Orchestrator

**适合场景**：
- 开发和测试阶段
- Gateway 独立功能验证
- 性能测试和压力测试
- CI/CD 自动化测试
