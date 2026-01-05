# 实时图欺诈检测系统 (Real-time Graph Fraud Detection System)

基于 Spring Boot 3.x、gRPC、Kafka、Redis、PostgreSQL 构建的高性能欺诈检测系统。

## 项目架构

### 技术栈

- **Java 21** - 最新 LTS 版本
- **Spring Boot 3.2.x** - 现代化 Spring 框架
- **WebFlux** - 响应式编程模型
- **gRPC** - 高性能 RPC 通信
- **Kafka** - 事件流处理
- **Redis** - 缓存和幂等性控制
- **PostgreSQL** - 持久化存储
- **Flyway** - 数据库版本管理
- **Resilience4j** - 弹性和容错
- **OpenTelemetry** - 可观测性

### 模块说明

```
risk-control/
├── contracts/           # Proto 定义和生成的 gRPC 代码
├── common/             # 通用工具、异常、响应、配置
├── graph-service/      # 图服务 gRPC Server (端口: 9091)
├── feature-service/    # 特征服务 gRPC Server (端口: 9092)
└── risk-gateway/       # 风险网关 WebFlux (端口: 8080)
```

#### 1. contracts

包含所有 gRPC 服务的 proto 定义：

- `fraud.model.v1.ModelServing` - 模型推理服务
- `fraud.graph.v1.GraphService` - 图操作服务
- `fraud.feature.v1.FeatureService` - 特征获取服务

#### 2. common

通用基础设施：

- 统一异常处理 (`BusinessException`, `ErrorCode`)
- 统一响应格式 (`Response<T>`)
- OpenTelemetry 追踪配置
- Jackson 序列化配置
- ID 生成器
- 日志配置

#### 3. graph-service

图数据库服务，提供：

- `UpsertNodes` - 节点插入/更新
- `UpsertEdges` - 边插入/更新
- `SampleSubgraph` - 子图采样 (BFS 遍历)

实现：内存图存储 (`InMemoryGraphRepository`)，可扩展至 Neo4j。

#### 4. feature-service

特征存储服务，提供：

- `GetFeatures` - 单个主体特征获取
- `BatchGetFeatures` - 批量特征获取

实现：Redis Hash 存储 + 在线计算兜底。

#### 5. risk-gateway

**核心网关服务**，编排整个风险评分流程：

**REST API:**
```
POST /api/v1/risk/score
```

**处理流程:**
1. 请求参数校验
2. Redis 幂等性检查
3. 调用 GraphService 采样子图
4. 调用 FeatureService 获取特征
5. 调用 ModelService (Python gRPC) 推理
6. 基于阈值做决策 (ALLOW/REVIEW/REJECT)
7. 审计日志入库 (PostgreSQL)
8. 发布 Kafka 事件
9. 返回响应

**降级策略 (Resilience4j):**
- 超时时间: 2-3秒
- 熔断器: 50% 失败率触发熔断
- 重试: 最多 2 次
- 兜底规则: 模型服务失败时使用规则引擎

## 快速开始

### 前置条件

- JDK 21+
- Gradle 8.x
- Docker & Docker Compose

### 1. 启动基础设施

```bash
# 启动 PostgreSQL, Redis, Kafka
docker-compose up -d

# 检查服务状态
docker-compose ps

# 查看日志
docker-compose logs -f
```

等待所有服务健康检查通过（约 30 秒）。

### 2. 初始化数据库

```bash
# 方法 1: 使用 Docker exec
docker exec -it risk-control-postgres psql -U postgres -d risk_control -f /docker-entrypoint-initdb.d/init.sql

# 方法 2: 直接运行 Flyway (需要先配置好连接)
cd risk-gateway
../gradlew flywayMigrate
```

**或** 首次启动 `risk-gateway` 时会自动执行 Flyway 迁移。

### 3. 编译项目

```bash
# 在项目根目录
./gradlew build

# 跳过测试编译
./gradlew build -x test
```

### 4. 启动服务

按以下顺序启动服务：

#### 方式 A: 使用 Gradle (推荐开发)

```bash
# Terminal 1: Graph Service
cd graph-service
../gradlew bootRun

# Terminal 2: Feature Service
cd feature-service
../gradlew bootRun

# Terminal 3: Risk Gateway
cd risk-gateway
../gradlew bootRun
```

#### 方式 B: 使用 Java 直接运行

```bash
# Graph Service
java -jar graph-service/build/libs/graph-service-1.0.0-SNAPSHOT.jar

# Feature Service
java -jar feature-service/build/libs/feature-service-1.0.0-SNAPSHOT.jar

# Risk Gateway
java -jar risk-gateway/build/libs/risk-gateway-1.0.0-SNAPSHOT.jar
```

### 5. 验证服务状态

```bash
# Graph Service (HTTP 端口)
curl http://localhost:8091/actuator/health

# Feature Service
curl http://localhost:8092/actuator/health

# Risk Gateway
curl http://localhost:8080/actuator/health

# Prometheus 指标
curl http://localhost:8080/actuator/metrics
```

## API 使用示例

### 请求风险评分

```bash
curl -X POST http://localhost:8080/api/v1/risk/score \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt_test_001",
    "subjectType": "user",
    "subjectId": "user_12345",
    "transactionAmount": 1500.00,
    "currency": "USD",
    "merchantId": "merchant_001",
    "deviceId": "device_abc",
    "ipAddress": "192.168.1.100",
    "modelVersion": "v1"
  }'
```

### 成功响应示例

```json
{
  "code": 0,
  "message": "Success",
  "requestId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "timestamp": 1704340800000,
  "data": {
    "requestId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "eventId": "evt_test_001",
    "subjectType": "user",
    "subjectId": "user_12345",
    "fraudScore": 0.23,
    "decision": "ALLOW",
    "reason": "Fraud score: 0.23, Decision: ALLOW",
    "modelVersion": "v1",
    "processingTimeMs": 245,
    "cached": false
  }
}
```

### 测试幂等性

使用相同的 `eventId` 再次请求，将返回缓存的结果 (`cached: true`)：

```bash
# 等待 10 分钟内重复请求
curl -X POST http://localhost:8080/api/v1/risk/score \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt_test_001",
    "subjectType": "user",
    "subjectId": "user_12345",
    "transactionAmount": 1500.00
  }'
```

## 配置说明

### gRPC 客户端配置 (risk-gateway/application.yml)

```yaml
grpc:
  client:
    graph-service:
      address: 'static://localhost:9091'
      negotiation-type: plaintext
    feature-service:
      address: 'static://localhost:9092'
      negotiation-type: plaintext
    model-service:
      address: 'static://localhost:9093'  # Python 模型服务
      negotiation-type: plaintext
```

### Resilience4j 弹性配置

```yaml
resilience4j:
  circuitbreaker:
    instances:
      modelService:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        sliding-window-size: 10
  timelimiter:
    instances:
      modelService:
        timeout-duration: 3s
```

### 决策阈值 (RiskScoringService.java)

```java
private static final double REJECT_THRESHOLD = 0.8;   // 拒绝阈值
private static final double REVIEW_THRESHOLD = 0.5;   // 人工审核阈值
```

可从数据库 `risk_threshold_config` 表动态加载。

## 测试

### 单元测试

```bash
# 测试所有模块
./gradlew test

# 测试单个模块
./gradlew :risk-gateway:test

# 查看测试报告
open risk-gateway/build/reports/tests/test/index.html
```

### 关键测试用例

`risk-gateway/src/test/java/com/riskcontrol/gateway/service/RiskScoringServiceTest.java`:

1. **正常流程测试** - 完整评分链路
2. **模型超时降级** - 验证兜底规则引擎
3. **Redis 幂等命中** - 验证缓存逻辑

### 性能测试

```bash
# 使用 Apache Bench
ab -n 1000 -c 10 -p request.json -T application/json \
  http://localhost:8080/api/v1/risk/score

# 使用 wrk
wrk -t4 -c100 -d30s -s request.lua \
  http://localhost:8080/api/v1/risk/score
```

## 监控与可观测性

### 健康检查端点

```bash
# 所有服务
curl http://localhost:8080/actuator/health
curl http://localhost:8091/actuator/health
curl http://localhost:8092/actuator/health
```

### Prometheus 指标

```bash
# 暴露的指标
curl http://localhost:8080/actuator/prometheus

# 关键指标
- http_server_requests_seconds
- resilience4j_circuitbreaker_state
- grpc_client_processing_seconds
- redis_commands_duration_seconds
```

### 日志示例

```log
2024-01-05 10:30:45.123 [grpc-server-ELG-1] INFO  c.r.g.service.GraphGrpcService - Sampled subgraph: 15 nodes, 28 edges in 45ms
2024-01-05 10:30:45.234 [boundedElastic-1] DEBUG c.r.g.service.RiskScoringService - Feature retrieval completed for requestId: abc-123
2024-01-05 10:30:45.345 [boundedElastic-1] INFO  c.r.g.service.RiskScoringService - Processing risk scoring request: requestId=abc-123, eventId=evt_test_001
```

## 故障排查

### 常见问题

#### 1. gRPC 连接失败

```bash
# 检查 gRPC 端口是否监听
netstat -an | grep 9091
netstat -an | grep 9092

# 检查防火墙
sudo ufw allow 9091/tcp
sudo ufw allow 9092/tcp
```

#### 2. PostgreSQL 连接失败

```bash
# 检查 Docker 容器状态
docker-compose ps postgres

# 查看日志
docker-compose logs postgres

# 测试连接
docker exec -it risk-control-postgres psql -U postgres -c "SELECT 1"
```

#### 3. Kafka 消息未发送

```bash
# 检查 Kafka 日志
docker-compose logs kafka

# 列出 topics
docker exec -it risk-control-kafka kafka-topics.sh --list --bootstrap-server localhost:9092

# 查看消息
docker exec -it risk-control-kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic risk_score_events \
  --from-beginning
```

#### 4. Redis 连接超时

```bash
# 测试 Redis
docker exec -it risk-control-redis redis-cli ping

# 查看键
docker exec -it risk-control-redis redis-cli keys "*"
```

## 扩展开发

### 添加新的 gRPC 服务

1. 在 `contracts/src/main/proto/` 定义 proto 文件
2. 运行 `./gradlew :contracts:generateProto` 生成 Java 代码
3. 创建新的 Spring Boot 模块 (如 `ml-service`)
4. 实现 `@GrpcService` 注解的服务类

### 切换到 Neo4j 图数据库

1. 添加 Neo4j 依赖到 `graph-service/build.gradle.kts`
2. 实现 `Neo4jGraphRepository` 实现 `GraphRepository` 接口
3. 创建配置类切换 Bean

### 集成真实 Python 模型服务

```python
# model_service.py (示例)
import grpc
from concurrent import futures
import fraud.model.v1.model_service_pb2 as pb2
import fraud.model.v1.model_service_pb2_grpc as grpc_pb2_grpc

class ModelServicer(grpc_pb2_grpc.ModelServingServicer):
    def Predict(self, request, context):
        # 调用实际 ML 模型 (TensorFlow/PyTorch/ONNX)
        fraud_score = self.model.predict(request.features)
        return pb2.PredictResponse(
            request_id=request.request_id,
            fraud_score=fraud_score,
            model_version="pytorch_v1"
        )

server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
grpc_pb2_grpc.add_ModelServingServicer_to_server(ModelServicer(), server)
server.add_insecure_port('[::]:9093')
server.start()
server.wait_for_termination()
```

## 性能调优

### JVM 参数

```bash
java -Xms2g -Xmx4g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -jar risk-gateway.jar
```

### 连接池配置

```yaml
spring:
  r2dbc:
    pool:
      initial-size: 10
      max-size: 50
  data:
    redis:
      lettuce:
        pool:
          max-active: 20
          max-idle: 10
```

### gRPC 线程池

```yaml
grpc:
  server:
    shutdown-grace-period: 30s
    max-connection-idle: 5m
```

## 生产部署

### Docker 镜像构建

```dockerfile
# Dockerfile (示例)
FROM eclipse-temurin:21-jre
COPY build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

```bash
docker build -t risk-gateway:1.0.0 .
docker push your-registry/risk-gateway:1.0.0
```

### Kubernetes 部署

```yaml
# deployment.yaml (示例)
apiVersion: apps/v1
kind: Deployment
metadata:
  name: risk-gateway
spec:
  replicas: 3
  selector:
    matchLabels:
      app: risk-gateway
  template:
    metadata:
      labels:
        app: risk-gateway
    spec:
      containers:
      - name: risk-gateway
        image: risk-gateway:1.0.0
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "2000m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 5
```

## 许可证

MIT License

## 联系方式

- 项目维护: Your Team
- 技术支持: support@example.com
