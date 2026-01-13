# Gateway Service

风险控制系统的统一API网关服务。

## 功能特性

- ✅ 统一API入口，接收所有交易评估请求
- ✅ 请求转发到 Risk Orchestrator Service
- ✅ 请求验证和错误处理
- ✅ 健康检查和监控接口
- ✅ 结构化日志记录
- ✅ 支持单个和批量交易处理（批量功能待实现）

## 技术栈

- **Java 17**
- **Spring Boot 3.x**
- **Spring WebFlux** - 响应式HTTP客户端
- **Spring Actuator** - 健康检查和监控
- **Lombok** - 减少样板代码
- **Jackson** - JSON序列化

## 项目结构

```
gateway-service/
├── src/main/java/com/risk/gateway/
│   ├── GatewayApplication.java          # 主入口类
│   ├── config/
│   │   ├── WebClientConfig.java         # WebClient配置
│   │   └── GatewayProperties.java       # 配置属性
│   ├── controller/
│   │   └── GatewayController.java       # REST控制器
│   ├── service/
│   │   └── GatewayService.java          # 业务逻辑
│   ├── model/
│   │   ├── TransactionRequest.java      # 请求模型
│   │   └── TransactionResponse.java     # 响应模型
│   └── exception/
│       └── GlobalExceptionHandler.java  # 全局异常处理
└── src/main/resources/
    └── application.yml                   # 配置文件
```

## 配置说明

### application.yml

```yaml
server:
  port: 8080  # Gateway服务端口

gateway:
  orchestrator-base-url: http://localhost:8081  # Orchestrator服务地址
  timeout-ms: 5000                              # 请求超时时间
  log-requests: true                            # 是否记录请求日志
  log-responses: true                           # 是否记录响应日志
```

## API接口

### 1. 交易评估接口

**POST** `/api/v1/transactions`

请求体示例：
```json
{
  "transactionId": "txn_123456",
  "userId": "user_789",
  "eventTimestamp": 1736608800000,
  "amount": 299.99,
  "currency": "USD",
  "productCd": "W",
  "channel": "online",
  "attributes": {
    "card1": 12345,
    "addr1": 100,
    "deviceInfo": "iPhone 12",
    "emailDomain": "gmail.com"
  }
}
```

响应示例：
```json
{
  "requestId": "req_uuid",
  "transactionId": "txn_123456",
  "decision": "APPROVE",
  "riskScore": 0.23,
  "reasons": ["low_risk_device", "trusted_user"],
  "processedAt": 1736608800123,
  "statusCode": 200,
  "message": "Success"
}
```

### 2. 批量交易评估接口（待实现）

**POST** `/api/v1/transactions/batch`

### 3. 健康检查接口

**GET** `/api/v1/health`

响应示例：
```json
{
  "status": "UP",
  "orchestrator": "UP",
  "timestamp": 1736608800123
}
```

### 4. 服务信息接口

**GET** `/api/v1/info`

响应示例：
```json
{
  "service": "gateway-service",
  "version": "1.0.0-SNAPSHOT",
  "description": "Risk Control Gateway Service - Unified API Gateway"
}
```

### 5. Actuator监控接口

**GET** `/actuator/health` - Spring Boot健康检查

**GET** `/actuator/info` - 应用信息

**GET** `/actuator/metrics` - 应用指标

## 快速开始

### 编译项目

```bash
cd services/gateway-service
mvn clean compile
```

### 运行服务

```bash
mvn spring-boot:run
```

或者直接打包后运行：

```bash
mvn clean package
java -jar target/gateway-service-1.0.0-SNAPSHOT.jar
```

### 测试接口

使用 cURL 测试：

```bash
# 健康检查
curl http://localhost:8080/api/v1/health

# 交易评估
curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "txn_test_001",
    "userId": "user_test_001",
    "eventTimestamp": 1736608800000,
    "amount": 299.99,
    "currency": "USD",
    "productCd": "W",
    "channel": "online",
    "attributes": {
      "card1": 12345,
      "addr1": 100
    }
  }'
```

## 与 txn-simulator 对接

Gateway Service 接受的请求格式与 `txn-simulator` 发送的格式完全兼容。

### txn-simulator 配置

在 `tools/txn-simulator/src/main/resources/application.yml` 中配置：

```yaml
simulator:
  target:
    base-url: "http://localhost:8080"
    endpoint: "/api/v1/transactions"
    type: GATEWAY
```

### 运行模拟测试

```bash
cd tools/txn-simulator
mvn spring-boot:run
```

## 依赖服务

Gateway Service 依赖以下服务：

- **Risk Orchestrator Service** (端口 8081) - 必须运行，否则会返回降级响应

## 日志

日志文件位置：`logs/gateway-service.log`

日志级别配置：
- `com.risk.gateway`: DEBUG
- `org.springframework.web`: INFO

## 监控指标

通过 Actuator 暴露的指标：
- JVM 内存使用
- HTTP 请求统计
- 自定义业务指标

## 错误处理

当 Orchestrator 服务不可用时，Gateway 会返回降级响应：

```json
{
  "requestId": "req_uuid",
  "transactionId": "txn_123456",
  "decision": "REVIEW",
  "riskScore": 0.5,
  "reasons": ["Service unavailable"],
  "statusCode": 503,
  "message": "Orchestrator service error: ..."
}
```

## 后续开发计划

- [ ] 实现批量交易处理
- [ ] 添加限流功能
- [ ] 添加熔断器
- [ ] 集成 API Key 认证
- [ ] 添加请求追踪 (Trace ID)
- [ ] 集成 Prometheus metrics
- [ ] 添加 Swagger API 文档

## 故障排查

### 问题：无法连接到 Orchestrator

**解决方案**：
1. 检查 Orchestrator 服务是否运行：`curl http://localhost:8081/actuator/health`
2. 检查配置文件中的 `orchestrator-base-url` 是否正确
3. 查看日志文件获取详细错误信息

### 问题：端口 8080 已被占用

**解决方案**：
修改 `application.yml` 中的 `server.port` 为其他端口。

## 许可证

Copyright © 2026 Risk Control System
