# 实时图欺诈检测系统 - 项目结构

```
risk-control/
│
├── contracts/                          # gRPC Proto 定义模块
│   ├── build.gradle.kts
│   └── src/main/proto/
│       └── fraud/
│           ├── model/v1/
│           │   └── model_service.proto        # 模型推理服务定义
│           ├── graph/v1/
│           │   └── graph_service.proto        # 图服务定义
│           └── feature/v1/
│               └── feature_service.proto      # 特征服务定义
│
├── common/                             # 通用基础设施模块
│   ├── build.gradle.kts
│   └── src/main/
│       ├── java/com/riskcontrol/common/
│       │   ├── config/
│       │   │   └── JacksonConfig.java        # Jackson 序列化配置
│       │   ├── error/
│       │   │   ├── ErrorCode.java            # 错误码枚举
│       │   │   └── BusinessException.java    # 业务异常类
│       │   ├── response/
│       │   │   └── Response.java             # 统一响应格式
│       │   ├── telemetry/
│       │   │   └── OpenTelemetryConfig.java  # OpenTelemetry 配置
│       │   └── util/
│       │       └── IdGenerator.java          # ID 生成器
│       └── resources/
│           └── logback.xml                   # 日志配置
│
├── graph-service/                      # 图数据库服务 (gRPC Server)
│   ├── build.gradle.kts
│   └── src/main/
│       ├── java/com/riskcontrol/graph/
│       │   ├── GraphServiceApplication.java  # 启动类
│       │   ├── config/
│       │   │   └── GrpcServerConfig.java     # gRPC 服务器配置
│       │   ├── model/
│       │   │   ├── GraphNode.java            # 图节点实体
│       │   │   └── GraphEdge.java            # 图边实体
│       │   ├── repository/
│       │   │   ├── GraphRepository.java      # 图存储接口
│       │   │   └── InMemoryGraphRepository.java  # 内存实现
│       │   └── service/
│       │       └── GraphGrpcService.java     # gRPC 服务实现
│       └── resources/
│           └── application.yml
│
├── feature-service/                    # 特征存储服务 (gRPC Server)
│   ├── build.gradle.kts
│   └── src/main/
│       ├── java/com/riskcontrol/feature/
│       │   ├── FeatureServiceApplication.java  # 启动类
│       │   ├── config/
│       │   │   └── RedisConfig.java           # Redis 配置
│       │   ├── repository/
│       │   │   └── FeatureRepository.java     # 特征存储 Repository
│       │   └── service/
│       │       └── FeatureGrpcService.java    # gRPC 服务实现
│       └── resources/
│           └── application.yml
│
├── risk-gateway/                       # 风险评分网关 (WebFlux)
│   ├── build.gradle.kts
│   └── src/main/
│       ├── java/com/riskcontrol/gateway/
│       │   ├── RiskGatewayApplication.java    # 启动类
│       │   ├── client/
│       │   │   ├── GraphClient.java          # Graph gRPC 客户端
│       │   │   ├── FeatureClient.java        # Feature gRPC 客户端
│       │   │   └── ModelClient.java          # Model gRPC 客户端
│       │   ├── config/
│       │   │   ├── DatabaseConfig.java       # R2DBC 数据库配置
│       │   │   ├── KafkaConfig.java          # Kafka 配置
│       │   │   ├── RedisConfig.java          # Redis 配置
│       │   │   ├── ResilienceConfig.java     # Resilience4j 配置
│       │   │   └── WebFluxConfig.java        # WebFlux 配置
│       │   ├── controller/
│       │   │   └── RiskGatewayController.java  # REST 控制器
│       │   ├── model/
│       │   │   ├── Decision.java             # 决策枚举
│       │   │   ├── RiskScoreRequest.java     # 请求模型
│       │   │   └── RiskScoreResponse.java    # 响应模型
│       │   ├── repository/
│       │   │   └── RiskAuditRepository.java  # 审计日志存储
│       │   └── service/
│       │       └── RiskScoringService.java   # 核心评分服务
│       └── resources/
│           ├── application.yml
│           └── db/migration/
│               └── V1__init.sql              # Flyway 数据库初始化
│
├── docker-compose.yml                  # Docker Compose 配置
├── build.gradle.kts                    # 根项目构建配置
├── settings.gradle.kts                 # Gradle 设置
├── gradle.properties                   # Gradle 属性
└── README.md                           # 项目文档
```

## 端口分配

| 服务 | HTTP 端口 | gRPC 端口 | 说明 |
|------|-----------|-----------|------|
| risk-gateway | 8080 | - | REST API 入口 |
| graph-service | 8091 | 9091 | 图数据库服务 |
| feature-service | 8092 | 9092 | 特征存储服务 |
| PostgreSQL | 5432 | - | 数据库 |
| Redis | 6379 | - | 缓存 |
| Kafka | 9092 | - | 消息队列 |
| Model Service (Python) | - | 9093 | 模型推理服务（预留） |

## 核心流程

### 风险评分流程 (risk-gateway)

```
HTTP POST /api/v1/risk/score
    ↓
[1] 参数校验 (Jakarta Validation)
    ↓
[2] Redis 幂等性检查
    ↓
[3] GraphClient.sampleSubgraph()     → graph-service:9091
    ↓
[4] FeatureClient.getFeatures()      → feature-service:9092
    ↓
[5] ModelClient.predict()            → model-service:9093 (Python)
    ↓ (如果失败，触发降级)
[6] 规则引擎兜底
    ↓
[7] 决策逻辑 (ALLOW/REVIEW/REJECT)
    ↓
[8] RiskAuditRepository.saveAudit() → PostgreSQL
    ↓
[9] KafkaTemplate.send()             → Kafka: risk_score_events
    ↓
[10] 返回 Response<RiskScoreResponse>
```

### 容错机制

- **Circuit Breaker**: 50% 失败率触发熔断，30秒半开状态
- **Time Limiter**: 每个调用 2-3 秒超时
- **Retry**: Graph/Feature 服务最多重试 2 次
- **Fallback**: Model 服务失败时使用规则引擎

## 数据模型

### RiskRequestAudit (PostgreSQL)

```sql
- id: BIGSERIAL (主键)
- request_id: VARCHAR (唯一)
- event_id: VARCHAR (业务幂等键)
- subject_type: VARCHAR (user/device/merchant)
- fraud_score: DECIMAL (0-1)
- decision: VARCHAR (ALLOW/REVIEW/REJECT)
- processing_time_ms: BIGINT
- cached: BOOLEAN
- request_body: JSONB
- created_at: TIMESTAMP
```

### Redis Keys

```
- dedup:event:{event_id}           # 幂等性缓存 (10分钟 TTL)
- feat:{version}:{type}:{id}       # 特征缓存 (Hash)
- nbr:{node_id}                    # 邻居节点缓存 (预留)
```

### Kafka Topics

```
- txn_events           # 交易事件流 (预留)
- edge_events          # 图边事件流 (预留)
- risk_score_events    # 风险评分事件
```

## 扩展点

1. **Neo4j 图数据库**: 实现 `GraphRepository` 接口，替换 `InMemoryGraphRepository`
2. **Kafka Streams 特征计算**: 在 `feature-service` 中添加流式处理模块
3. **Prometheus + Grafana**: 集成完整监控面板
4. **Zipkin/Jaeger**: 启用分布式追踪
5. **Python 模型服务**: 独立部署 TensorFlow/PyTorch gRPC 服务
