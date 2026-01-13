# Orchestrator Service

风控系统的核心编排服务，负责协调特征服务、决策服务和Python模型推理服务。

## 功能特性

- ✅ 统一评估接口，接收来自Gateway的请求
- ✅ 多种编排模式：混合模式、纯规则模式、纯模型模式
- ✅ gRPC客户端调用Python推理服务
- ✅ HTTP客户端调用Feature Service和Decision Service
- ✅ 智能降级策略（模型失败时降级到规则）
- ✅ Mock模式（独立测试，无需下游服务）
- ✅ 完善的错误处理和日志记录

## 技术栈

- **Java 17**
- **Spring Boot 3.x**
- **Spring WebFlux** - 响应式HTTP客户端
- **gRPC** - 调用Python推理服务
- **Lombok** - 减少样板代码
- **Protobuf** - 协议缓冲区

## 项目结构

```
orchestrator-service/
├── src/main/java/com/risk/orch/
│   ├── OrchestratorApplication.java    # 主应用类
│   ├── config/
│   │   ├── OrchestratorProperties.java # 配置属性
│   │   ├── WebClientConfig.java        # HTTP客户端配置
│   │   └── GrpcConfig.java             # gRPC客户端配置
│   ├── controller/
│   │   └── OrchestratorController.java # REST控制器
│   ├── service/
│   │   └── OrchestratorService.java    # 核心编排逻辑
│   ├── model/
│   │   ├── OrchestratorRequest.java    # 请求模型
│   │   └── OrchestratorResponse.java   # 响应模型
│   └── exception/
│       └── GlobalExceptionHandler.java # 全局异常处理
└── src/main/resources/
    ├── application.yml                  # 配置文件（默认mock模式）
    └── application-mock.yml             # Mock模式配置
```

## 配置说明

### application.yml

```yaml
orchestrator:
  # 下游服务地址
  feature-service-url: http://localhost:8082
  decision-service-url: http://localhost:8083
  python-inference-host: localhost
  python-inference-port: 50051

  # 超时设置（毫秒）
  feature-service-timeout-ms: 2000
  decision-service-timeout-ms: 1000
  python-inference-timeout-ms: 3000

  # 编排模式
  mode: HYBRID  # HYBRID, RULES_ONLY, MODEL_ONLY

  # 降级策略
  enable-fallback: true  # 模型失败时降级到规则

  # 风险评分阈值
  review-threshold: 0.5
  reject-threshold: 0.7

  # Mock模式（独立测试）
  mock-mode: true
```

## 编排模式

### 1. HYBRID（混合模式）
默认模式，综合使用规则和模型：
```
1. 调用Feature Service获取特征
2. 调用Decision Service执行规则 → 规则分数
3. 调用Python模型推理 → 模型分数
4. 综合决策：模型70% + 规则30%
```

### 2. RULES_ONLY（纯规则模式）
只使用规则引擎：
```
1. 调用Feature Service获取特征
2. 调用Decision Service执行规则
3. 直接返回规则决策
```

### 3. MODEL_ONLY（纯模型模式）
只使用Python模型：
```
1. 构建gRPC请求
2. 调用Python推理服务
3. 直接返回模型决策
```

## API接口

### 主评估接口

**POST** `/api/v1/evaluate`

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
  "transactionId": "txn_123456",
  "decision": "APPROVE",
  "riskScore": 0.23,
  "reasons": ["hybrid_decision"],
  "processedAt": 1736608800123,
  "modelUsed": "hybrid",
  "rulesTriggered": ["normal_amount"],
  "fallbackUsed": null,
  "debugInfo": null
}
```

### 其他接口

**GET** `/api/v1/health` - 健康检查
**GET** `/api/v1/info` - 服务信息

## gRPC 通信

### 与Python推理服务的通信

Orchestrator Service使用gRPC调用Python推理服务：

```protobuf
service RiskInfraService {
  rpc Inference (InferenceRequest) returns (InferenceResponse);
}
```

**请求构建**：
- 将HTTP请求转换为gRPC `InferenceRequest`
- 包含交易上下文（TransactionContext）
- 包含推理选项（InferenceOptions）

**响应解析**：
- 提取决策（APPROVE/REVIEW/REJECT）
- 提取风险评分（riskScore）
- 提取原因列表（topReasons）

## 降级策略

当Python模型调用失败时：

1. 如果 `enable-fallback: true`：
   - 使用规则引擎的决策
   - 设置 `fallbackUsed: "model_to_rules"`

2. 如果 `enable-fallback: false`：
   - 返回默认决策 "REVIEW"
   - 返回默认风险评分 0.5
   - 添加原因 "model_failed"

## Mock模式

### 启用Mock模式

在 `application.yml` 中设置：
```yaml
orchestrator:
  mock-mode: true
```

### Mock逻辑

Mock模式下，不调用任何下游服务，直接基于金额返回决策：

- **金额 > 5000**：REJECT（分数0.75）
- **金额 > 1000**：REVIEW（分数0.55）
- **金额 ≤ 1000**：APPROVE（分数0.15）

### 使用场景

- 独立测试Orchestrator Service
- 开发阶段，下游服务未就绪
- 演示和调试

## 快速开始

### 1. 启动服务（Mock模式）

```bash
cd services/risk-orchestrator-service
start.bat
```

或手动启动：
```bash
mvn spring-boot:run
```

### 2. 测试接口

```bash
# 健康检查
curl http://localhost:8081/api/v1/health

# 评估交易
curl -X POST http://localhost:8081/api/v1/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "test001",
    "userId": "user001",
    "eventTimestamp": 1736608800000,
    "amount": 299.99,
    "currency": "USD",
    "productCd": "W",
    "channel": "online",
    "attributes": {"card1": 12345, "addr1": 100}
  }'
```

### 3. 运行测试脚本

```bash
test-orchestrator.bat
```

## 与Gateway Service对接

### 1. 启动Orchestrator Service

```bash
cd services/risk-orchestrator-service
mvn spring-boot:run
```

### 2. 配置Gateway Service

编辑 `services/gateway-service/src/main/resources/application.yml`：

```yaml
gateway:
  orchestrator-base-url: http://localhost:8081
```

### 3. 启动Gateway Service

```bash
cd services/gateway-service
mvn spring-boot:run
```

### 4. 测试完整流程

```bash
# 通过Gateway测试
curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "test001",
    "userId": "user001",
    "eventTimestamp": 1736608800000,
    "amount": 299.99,
    "currency": "USD",
    "productCd": "W",
    "channel": "online",
    "attributes": {"card1": 12345, "addr1": 100}
  }'
```

请求流程：
```
Client → Gateway (8080) → Orchestrator (8081) → Mock/Python Model
```

## 与Python服务对接

### 1. 启动Python推理服务

确保Python gRPC服务运行在 `localhost:50051`

### 2. 配置Orchestrator

编辑 `application.yml`：

```yaml
orchestrator:
  mock-mode: false  # 关闭Mock模式
  python-inference-host: localhost
  python-inference-port: 50051
  mode: HYBRID  # 或 MODEL_ONLY
```

### 3. 重启Orchestrator Service

```bash
mvn spring-boot:run
```

### 4. 测试gRPC通信

发送评估请求，Orchestrator会通过gRPC调用Python服务。

## 日志

日志文件位置：`logs/orchestrator-service.log`

关键日志：
- 评估请求：`Evaluating transaction: transactionId=...`
- 模式信息：`mode=HYBRID`
- 规则评估：`Rule evaluation: decision=..., score=...`
- 模型评估：`Python model response: decision=..., score=...`
- 最终决策：`Evaluation completed: decision=..., score=...`
- 错误信息：`Python model evaluation failed: ...`

## 监控

### Spring Actuator

- `GET /actuator/health` - 服务健康状态
- `GET /actuator/info` - 应用信息
- `GET /actuator/metrics` - 性能指标

### 自定义端点

- `GET /api/v1/health` - 业务健康检查

## 故障排查

### 问题1：gRPC连接失败

**症状**：`Python model evaluation failed: Connection refused`

**解决方案**：
1. 检查Python服务是否运行：`netstat -an | findstr 50051`
2. 检查配置：`python-inference-host` 和 `python-inference-port`
3. 使用Mock模式测试

### 问题2：编译失败

**症状**：`找不到符号: 方法 Builder`

**解决方案**：
1. 确保Lombok正确配置
2. 删除target目录：`mvn clean`
3. 重新编译：`mvn compile`

### 问题3：端口8081被占用

**解决方案**：
修改 `application.yml` 中的 `server.port`

## 性能优化

### 1. 调整超时时间

根据实际响应时间调整：
```yaml
feature-service-timeout-ms: 2000
decision-service-timeout-ms: 1000
python-inference-timeout-ms: 3000
```

### 2. 选择合适的模式

- **高性能场景**：RULES_ONLY（规则执行快）
- **高精度场景**：MODEL_ONLY（模型准确度高）
- **平衡场景**：HYBRID（准确度和性能平衡）

### 3. 启用降级

确保 `enable-fallback: true`，避免模型失败导致请求阻塞

## 后续开发计划

- [ ] 实现真实的Feature Service调用
- [ ] 实现真实的Decision Service调用
- [ ] 添加缓存机制
- [ ] 添加请求追踪（Trace ID）
- [ ] 添加Prometheus metrics
- [ ] 添加Swagger API文档
- [ ] 实现批量评估接口
- [ ] 添加A/B测试支持

## 许可证

Copyright © 2026 Risk Control System
