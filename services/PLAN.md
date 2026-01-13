# 交易金融欺诈系统 - 服务功能规划与接口设计

## 系统架构概述

本系统采用微服务架构,实现实时金融交易欺诈检测。核心组件包括:

```
┌─────────────┐
│   Gateway   │  API网关 - 统一入口
└──────┬──────┘
       │
       ▼
┌──────────────┐
│ Orchestrator │  风险编排 - 协调各服务
└──────┬───────┘
       │
   ┌───┴──────────────────────┐
   │                          │
   ▼                          ▼
┌──────────┐           ┌──────────────┐
│ Feature  │           │   Decision   │  规则引擎
│ Service  │           │   Service    │
└─────┬────┘           └──────────────┘
      │
      ▼
┌──────────────┐
│ Transaction  │  交易历史与图谱
│   Service    │
└──────────────┘

   (gRPC)
      │
      ▼
┌──────────────┐
│  Python ML   │  图神经网络推理
│    Model     │
└──────────────┘
```

---

## 1. Gateway Service (网关服务)

### 职责
- 统一API入口,接收外部交易评估请求
- 请求认证与授权
- 限流与熔断
- 请求日志与监控
- 路由到编排服务

### 核心功能

#### 1.1 交易评估接口
**实现状态**: ✅ 已实现

**接口**: `POST /api/v1/evaluate`

**功能**: 接收交易请求,转发到编排服务进行风险评估

**请求参数**:
```json
{
  "transactionId": "txn_20250111_001",
  "userId": "user_12345",
  "eventTimestamp": 1736640000000,
  "amount": 1500.00,
  "currency": "USD",
  "productCd": "PAYMENT",
  "channel": "online",
  "attributes": {
    "merchantCategory": "electronics",
    "ipAddress": "192.168.1.1",
    "deviceId": "device_abc"
  }
}
```

**响应参数**:
```json
{
  "requestId": "req-uuid-001",
  "transactionId": "txn_20250111_001",
  "decision": "APPROVE",  // APPROVE, REVIEW, REJECT
  "riskScore": 0.25,      // 0-1,越高越风险
  "reasons": ["normal_amount"],
  "processedAt": 1736640001500,
  "statusCode": 200,
  "message": "Success"
}
```

**错误处理**:
- 400: 参数校验失败
- 429: 限流触发
- 500: 服务异常(降级为REVIEW)
- 503: 下游服务不可用

#### 1.2 健康检查接口
**实现状态**: ✅ 已实现

**接口**: `GET /actuator/health`

#### 1.3 测试接口
**实现状态**: ✅ 已实现

**接口**: `POST /api/v1/test/evaluate`

### 待增强功能

#### 1.4 批量评估接口
**接口**: `POST /api/v1/batch-evaluate`

**功能**: 批量评估多个交易

**请求参数**:
```json
{
  "transactions": [
    { /* TransactionRequest */ },
    { /* TransactionRequest */ }
  ]
}
```

**响应参数**:
```json
{
  "requestId": "batch-req-001",
  "results": [
    { /* TransactionResponse */ },
    { /* TransactionResponse */ }
  ],
  "summary": {
    "total": 100,
    "approved": 80,
    "review": 15,
    "rejected": 5
  }
}
```

#### 1.5 用户风险画像查询
**接口**: `GET /api/v1/users/{userId}/risk-profile`

**功能**: 查询用户历史风险画像

**响应参数**:
```json
{
  "userId": "user_12345",
  "riskLevel": "LOW",
  "avgRiskScore": 0.23,
  "totalTxns": 150,
  "rejectedTxns": 2,
  "reviewedTxns": 8,
  "lastUpdated": 1736640000000
}
```

#### 1.6 监控指标接口
**接口**: `GET /actuator/metrics/risk.*`

**指标**:
- `risk.requests.total`: 总请求数
- `risk.requests.latency`: 请求延迟
- `risk.decisions.{approve|review|reject}`: 决策分布
- `risk.errors.total`: 错误总数

---

## 2. Risk Orchestrator Service (风险编排服务)

### 职责
- 编排特征提取、规则评估、模型推理流程
- 决策融合(Hybrid/Model-Only/Rules-Only)
- 降级与容错处理
- 调用Python ML模型(gRPC)
- 生成最终决策

### 核心功能

#### 2.1 评估接口
**实现状态**: ✅ 已实现(基础版)

**接口**: `POST /api/v1/evaluate`

**功能**: 执行完整的风险评估流程

**流程**:
```
1. 接收交易请求
2. 调用 Feature Service 提取特征
3. 调用 Decision Service 评估规则
4. 调用 Python ML Model 进行图推理
5. 决策融合
6. 返回结果
```

**配置模式**:
- `HYBRID`: 模型(70%) + 规则(30%)
- `MODEL_ONLY`: 仅模型
- `RULES_ONLY`: 仅规则
- `MOCK`: 模拟模式(测试用)

**请求参数**: 同Gateway的TransactionRequest

**响应参数**:
```json
{
  "transactionId": "txn_001",
  "decision": "APPROVE",
  "riskScore": 0.35,
  "reasons": ["hybrid_decision"],
  "rulesTriggered": ["high_amount"],
  "modelUsed": "hybrid",
  "fallbackUsed": null,
  "processedAt": 1736640001500,
  "debugInfo": "Optional debug info"
}
```

#### 2.2 特征提取子流程
**实现状态**: ⚠️ 占位实现

**接口**: `POST /api/v1/features/extract` (内部服务)

**功能**: 从交易上下文中提取风险特征

**特征类别**:

**1. 用户特征**
- 用户注册时长
- 历史交易成功率
- 平均交易金额
- 交易频率(24h/7d/30d)
- 地理位置变化
- 设备指纹变化

**2. 交易特征**
- 交易金额
- 交易时间(小时/星期/月)
- 货币类型
- 产品类型
- 渠道类型

**3. 商户特征**
- 商户类别(MCC)
- 商户风险等级
- 商户历史欺诈率

**4. 关系特征**
- 交易对手方关系
- 资金流向网络
- 关联账户风险

**5. 行为特征**
- 登录频率
- 登录地点变化
- 设备切换频率
- 异常操作检测

**实现计划**:
```java
// 伪代码
public Map<String, Object> extractFeatures(OrchestratorRequest request) {
    // 1. 从 Transaction Service 获取用户历史
    UserHistory history = transactionService.getUserHistory(request.getUserId());

    // 2. 实时特征计算
    Map<String, Object> features = new HashMap<>();

    // 用户特征
    features.put("user_age_days", history.getAccountAgeDays());
    features.put("txn_count_24h", history.getTxnCountLast24h());
    features.put("avg_amount_7d", history.getAvgAmountLast7d());

    // 交易特征
    features.put("amount", request.getAmount());
    features.put("amount_zscore", calculateZScore(request.getAmount(), history));
    features.put("is_night_time", isNightTime(request.getEventTimestamp()));

    // 商户特征
    features.put("merchant_risk_score", getMerchantRiskScore(request.getMerchantId()));

    // ... 更多特征

    return features;
}
```

#### 2.3 规则评估子流程
**实现状态**: ⚠️ 简单占位实现

**接口**: `POST /api/v1/rules/evaluate` (内部服务)

**功能**: 执行预定义的欺诈检测规则

**规则类型**:

**1. 黑名单规则**
```yaml
- name: "blacklisted_user"
  condition: "user in blacklist"
  action: "REJECT"
  score_add: 1.0
```

**2. 金额规则**
```yaml
- name: "extremely_high_amount"
  condition: "amount > 10000"
  action: "REVIEW"
  score_add: 0.5

- name: "high_amount"
  condition: "amount > 5000"
  action: "REVIEW"
  score_add: 0.3
```

**3. 频率规则**
```yaml
- name: "high_frequency_24h"
  condition: "txn_count_24h > 10"
  action: "REVIEW"
  score_add: 0.4

- name: "velocity_check"
  condition: "txn_count_1h > 5"
  action: "REVIEW"
  score_add: 0.3
```

**4. 地理规则**
```yaml
- name: "cross_country"
  condition: "current_country != last_country"
  action: "REVIEW"
  score_add: 0.4

- name: "impossible_travel"
  condition: "travel_speed > 800 km/h"
  action: "REVIEW"
  score_add: 0.6
```

**5. 设备规则**
```yaml
- name: "new_device"
  condition: "device not seen in last 30 days"
  action: "REVIEW"
  score_add: 0.2

- name: "device_fingerprint_mismatch"
  condition: "fingerprint_changed"
  action: "REVIEW"
  score_add: 0.4
```

**6. 行为规则**
```yaml
- name: "unusual_time"
  condition: "txn_time between 02:00-05:00 and not habitual"
  action: "REVIEW"
  score_add: 0.2

- name: "account_takeover_risk"
  condition: "password_changed + new_device + high_amount"
  action: "REVIEW"
  score_add: 0.5
```

**实现计划**:
```java
// 规则引擎伪代码
public RuleEvaluationResult evaluateRules(
    OrchestratorRequest request,
    Map<String, Object> features
) {
    double totalScore = 0.0;
    List<String> triggeredRules = new ArrayList<>();

    for (Rule rule : ruleEngine.getRules()) {
        if (rule.matches(request, features)) {
            totalScore += rule.getScoreAdd();
            triggeredRules.add(rule.getName());
        }
    }

    String decision = scoreToDecision(totalScore);

    return RuleEvaluationResult.builder()
        .decision(decision)
        .score(totalScore)
        .triggeredRules(triggeredRules)
        .build();
}
```

#### 2.4 模型推理子流程(gRPC)
**实现状态**: ✅ 已实现

**协议**: gRPC (protobuf)

**Python模型服务**: `RiskInfraService`

**请求**: `InferenceRequest`
```protobuf
message InferenceRequest {
  string request_id = 1;
  int64 request_timestamp_ms = 2;
  string model_name = 3;
  string model_version = 4;
  string feature_version = 5;

  TransactionContext tx = 10;
  InferenceOptions options = 11;
}

message TransactionContext {
  string transaction_id = 1;
  string user_id = 2;
  int64 event_timestamp_ms = 3;
  double amount = 4;
  string currency = 5;
  string channel = 6;
  string product_cd = 7;
  map<string, FeatureValue> attributes = 10;
}
```

**响应**: `InferenceResponse`
```protobuf
message InferenceResponse {
  ResponseMeta meta = 1;
  string decision = 2;  // APPROVE, REVIEW, REJECT
  double risk_score = 3;
  repeated string top_reasons = 4;
  FloatVector embedding = 5;
}
```

#### 2.5 决策融合策略
**实现状态**: ✅ 已实现

**Hybrid模式**:
```
finalScore = (modelScore * 0.7) + (ruleScore * 0.3)
decision = scoreToDecision(finalScore)
```

**降级策略**:
- 模型失败 → 降级到规则(如果配置)
- 规则失败 → 仅使用模型
- 全部失败 → 返回REVIEW

#### 2.6 健康检查
**接口**: `GET /actuator/health`

#### 2.7 配置管理接口
**接口**: `GET /api/v1/config`

**功能**: 获取当前配置(模式、阈值等)

**响应参数**:
```json
{
  "mode": "HYBRID",
  "rejectThreshold": 0.7,
  "reviewThreshold": 0.4,
  "enableFallback": true,
  "pythonInferenceTimeoutMs": 3000
}
```

---

## 3. Transaction Service (交易服务)

### 职责
- 交易数据存储与查询
- 用户历史统计
- 交易图谱构建
- 实时交易流处理

### 核心功能

#### 3.1 交易记录存储
**接口**: `POST /api/v1/transactions`

**功能**: 保存交易记录

**请求参数**:
```json
{
  "transactionId": "txn_001",
  "userId": "user_123",
  "amount": 1500.00,
  "currency": "USD",
  "status": "COMPLETED",
  "decision": "APPROVE",
  "riskScore": 0.25,
  "eventTimestamp": 1736640000000,
  "attributes": {
    "merchantId": "merchant_456",
    "merchantCategory": "electronics",
    "channel": "online"
  }
}
```

**响应参数**:
```json
{
  "success": true,
  "transactionId": "txn_001",
  "createdAt": 1736640001000
}
```

#### 3.2 查询交易详情
**接口**: `GET /api/v1/transactions/{transactionId}`

**响应参数**:
```json
{
  "transactionId": "txn_001",
  "userId": "user_123",
  "amount": 1500.00,
  "currency": "USD",
  "status": "COMPLETED",
  "decision": "APPROVE",
  "riskScore": 0.25,
  "eventTimestamp": 1736640000000,
  "processedAt": 1736640001500,
  "attributes": { ... }
}
```

#### 3.3 用户历史查询
**接口**: `GET /api/v1/users/{userId}/history`

**功能**: 获取用户历史交易与统计信息

**查询参数**:
- `startTime`: 开始时间戳
- `endTime`: 结束时间戳
- `limit`: 返回数量

**响应参数**:
```json
{
  "userId": "user_123",
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
    { /* Transaction */ },
    // ...
  ]
}
```

#### 3.4 用户时间序列统计
**接口**: `GET /api/v1/users/{userId}/timeseries`

**查询参数**:
- `window`: `1h` | `24h` | `7d` | `30d`
- `metric`: `count` | `amount` | `avg_amount`

**响应参数**:
```json
{
  "userId": "user_123",
  "window": "24h",
  "metric": "count",
  "data": [
    {"timestamp": 1736640000000, "value": 5},
    {"timestamp": 1736636400000, "value": 3},
    // ...
  ]
}
```

#### 3.5 交易图谱查询
**接口**: `POST /api/v1/graph/query`

**功能**: 查询交易相关子图(用于图神经网络)

**请求参数**:
```json
{
  "centerEntityId": "user_123",
  "entityType": "USER",
  "hops": 2,
  "maxNodes": 100,
  "edgeTypes": ["USER_TO_TRANSACTION", "USER_TO_DEVICE", "USER_TO_MERCHANT"]
}
```

**响应参数**:
```json
{
  "nodes": [
    {
      "id": "user_123",
      "type": "USER",
      "features": {
        "age_days": 180,
        "txn_count": 150
      }
    },
    {
      "id": "txn_001",
      "type": "TRANSACTION",
      "features": {
        "amount": 1500.0,
        "timestamp": 1736640000000
      }
    },
    // ...
  ],
  "edges": [
    {
      "source": "user_123",
      "target": "txn_001",
      "type": "USER_TO_TRANSACTION",
      "features": {
        "timestamp": 1736640000000
      }
    },
    // ...
  ],
  "subgraph": {
    // 可选: 直接返回 SubGraph protobuf 格式
  }
}
```

#### 3.6 交易对手方查询
**接口**: `GET /api/v1/users/{userId}/counterparties`

**功能**: 查询用户交易对手方(商户、其他用户)

**查询参数**:
- `type`: `MERCHANT` | `USER` | `ALL`
- `limit`: 返回数量

**响应参数**:
```json
{
  "userId": "user_123",
  "counterparties": [
    {
      "id": "merchant_456",
      "type": "MERCHANT",
      "transactionCount": 15,
      "totalAmount": 7500.00,
      "lastTransactionTime": 1736640000000,
      "riskScore": 0.15
    },
    // ...
  ]
}
```

#### 3.7 批量交易查询
**接口**: `POST /api/v1/transactions/batch-query`

**功能**: 根据交易ID列表批量查询

**请求参数**:
```json
{
  "transactionIds": ["txn_001", "txn_002", "txn_003"]
}
```

**响应参数**:
```json
{
  "transactions": [
    { /* Transaction */ },
    // ...
  ]
}
```

#### 3.8 实时交易统计(用于特征计算)
**接口**: `GET /api/v1/users/{userId}/realtime-stats`

**功能**: 获取实时统计特征(缓存)

**响应参数**:
```json
{
  "userId": "user_123",
  "stats": {
    "txn_count_1h": 2,
    "txn_count_24h": 8,
    "txn_count_7d": 35,
    "amount_sum_24h": 3500.00,
    "amount_avg_7d": 480.00,
    "amount_max_30d": 5000.00,
    "last_txn_time": 1736640000000,
    "unique_merchants_30d": 12,
    "unique_devices_30d": 2
  },
  "cachedAt": 1736640001000
}
```

### 数据模型设计

#### Transaction (交易表)
```sql
CREATE TABLE transactions (
  transaction_id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  amount DECIMAL(18,2) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  status VARCHAR(20) NOT NULL,
  decision VARCHAR(20) NOT NULL,
  risk_score DECIMAL(5,4) NOT NULL,
  event_timestamp BIGINT NOT NULL,
  processed_at BIGINT NOT NULL,
  created_at BIGINT NOT NULL,

  -- 扩展字段
  product_cd VARCHAR(20),
  channel VARCHAR(20),
  merchant_id VARCHAR(64),
  device_id VARCHAR(64),
  ip_address VARCHAR(45),

  -- JSON 属性
  attributes JSON,

  INDEX idx_user_id (user_id),
  INDEX idx_event_timestamp (event_timestamp),
  INDEX idx_user_time (user_id, event_timestamp)
);
```

#### UserStats (用户统计表 - 定期更新)
```sql
CREATE TABLE user_stats (
  user_id VARCHAR(64) PRIMARY KEY,
  total_txns INT NOT NULL,
  total_amount DECIMAL(18,2) NOT NULL,
  avg_amount DECIMAL(18,2) NOT NULL,
  approved_txns INT NOT NULL,
  reviewed_txns INT NOT NULL,
  rejected_txns INT NOT NULL,
  account_age_days INT NOT NULL,
  last_txn_time BIGINT,
  updated_at BIGINT NOT NULL
);
```

#### GraphNodes (图节点表)
```sql
CREATE TABLE graph_nodes (
  node_id VARCHAR(128) PRIMARY KEY,
  node_type VARCHAR(20) NOT NULL,  -- USER, TRANSACTION, DEVICE, MERCHANT, IP
  features JSON NOT NULL,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  INDEX idx_node_type (node_type)
);
```

#### GraphEdges (图边表)
```sql
CREATE TABLE graph_edges (
  edge_id VARCHAR(128) PRIMARY KEY,
  source_node_id VARCHAR(128) NOT NULL,
  target_node_id VARCHAR(128) NOT NULL,
  edge_type VARCHAR(40) NOT NULL,
  features JSON,
  created_at BIGINT NOT NULL,
  INDEX idx_source (source_node_id),
  INDEX idx_target (target_node_id),
  INDEX idx_source_type (source_node_id, edge_type)
);
```

### 技术选型建议
- **数据库**: MySQL + Redis(缓存)
- **图数据库**: Neo4j / NebulaGraph (可选)
- **流处理**: Apache Kafka + Flink (实时特征)

---

## 4. Feature Service (特征服务)

### 职责
- 实时特征提取与计算
- 特征存储与管理
- 特征版本控制
- 特征缓存加速

### 核心功能

#### 4.1 批量特征提取
**接口**: `POST /api/v1/features/extract`

**功能**: 为交易批量提取特征

**请求参数**:
```json
{
  "transactionId": "txn_001",
  "userId": "user_123",
  "amount": 1500.00,
  "currency": "USD",
  "eventTimestamp": 1736640000000,
  "attributes": {
    "merchantId": "merchant_456",
    "channel": "online"
  }
}
```

**响应参数**:
```json
{
  "transactionId": "txn_001",
  "features": {
    // 用户特征
    "user_age_days": 180,
    "user_total_txns": 150,
    "user_approval_rate": 0.933,
    "user_avg_amount": 566.67,

    // 实时行为特征
    "txn_count_1h": 2,
    "txn_count_24h": 8,
    "txn_count_7d": 35,
    "amount_sum_24h": 3500.00,
    "amount_avg_7d": 480.00,

    // 交易特征
    "amount": 1500.00,
    "amount_zscore_30d": 1.85,
    "is_night_time": false,
    "is_weekend": true,

    // 商户特征
    "merchant_risk_score": 0.15,
    "merchant_txn_count_user": 5,

    // 设备特征
    "device_is_new": false,
    "device_age_days": 45,
    "device_txn_count_30d": 12,

    // 地理特征
    "ip_country_match": true,
    "ip_distance_last": 150.5,  // km

    // 时间特征
    "hour_of_day": 14,
    "day_of_week": 6,
    "day_of_month": 11
  },
  "featureVersion": "2026-01-11",
  "extractedAt": 1736640001500
}
```

#### 4.2 单个特征查询
**接口**: `GET /api/v1/features/{userId}?feature_names=txn_count_24h,amount_avg_7d`

**响应参数**:
```json
{
  "userId": "user_123",
  "features": {
    "txn_count_24h": 8,
    "amount_avg_7d": 480.00
  },
  "cached": true,
  "cachedAt": 1736640001000
}
```

#### 4.3 特征定义管理
**接口**: `GET /api/v1/features/definitions`

**功能**: 获取所有特征定义

**响应参数**:
```json
{
  "features": [
    {
      "name": "txn_count_24h",
      "type": "INTEGER",
      "category": "USER_BEHAVIOR",
      "description": "Number of transactions in last 24 hours",
      "defaultValue": 0,
      "version": "1.0"
    },
    // ...
  ]
}
```

#### 4.4 特征组提取
**接口**: `POST /api/v1/features/extract-by-group`

**功能**: 按特征组提取(仅提取特定类别特征)

**请求参数**:
```json
{
  "transactionId": "txn_001",
  "userId": "user_123",
  "groups": ["USER_BEHAVIOR", "TRANSACTION"]
}
```

**特征分组**:
- `USER_PROFILE`: 用户基础特征
- `USER_BEHAVIOR`: 用户行为特征
- `TRANSACTION`: 交易特征
- `MERCHANT`: 商户特征
- `DEVICE`: 设备特征
- `GEOGRAPHIC`: 地理特征
- `TEMPORAL`: 时间特征
- `RELATIONSHIP`: 关系特征

#### 4.5 特征历史查询
**接口**: `GET /api/v1/features/{userId}/history`

**查询参数**:
- `featureName`: 特征名称
- `startTime`: 开始时间
- `endTime`: 结束时间

**响应参数**:
```json
{
  "userId": "user_123",
  "featureName": "txn_count_24h",
  "history": [
    {"timestamp": 1736640000000, "value": 8},
    {"timestamp": 1736636400000, "value": 7},
    // ...
  ]
}
```

#### 4.6 特征更新通知
**接口**: `POST /api/v1/features/refresh`

**功能**: 触发特征重新计算与缓存更新

**请求参数**:
```json
{
  "userId": "user_123",
  "forceRefresh": true
}
```

### 特征计算逻辑

#### 实时特征 (需要快速计算)
```java
// 示例: 24小时内交易次数
public int getTxnCountLast24h(String userId) {
  String cacheKey = "user:" + userId + ":txn_count_24h";
  Integer cached = redis.get(cacheKey);
  if (cached != null) return cached;

  int count = transactionRepository.countByUserIdAndTimeRange(
    userId,
    System.currentTimeMillis() - 24 * 3600 * 1000,
    System.currentTimeMillis()
  );

  redis.setex(cacheKey, 300, count);  // 5分钟缓存
  return count;
}
```

#### 统计特征 (可预计算)
```java
// 示例: 金额Z-score
public double calculateAmountZScore(double amount, String userId) {
  UserStats stats = getUserStats(userId);  // 从预计算表获取
  double stdDev = stats.getAmountStdDev();
  double mean = stats.getAvgAmount();

  if (stdDev == 0) return 0.0;
  return (amount - mean) / stdDev;
}
```

#### 交叉特征
```java
// 示例: 金额/频率比
public double calculateAmountToFrequencyRatio(String userId) {
  double amount24h = getAmountSumLast24h(userId);
  int count24h = getTxnCountLast24h(userId);

  return count24h > 0 ? amount24h / count24h : 0.0;
}
```

### 缓存策略
- **热数据**: Redis (5-15分钟TTL)
- **温数据**: 本地缓存 (Caffeine, 1-5分钟)
- **冷数据**: 直接查询数据库

---

## 5. Decision Service (决策规则服务)

### 职责
- 规则管理与执行
- 规则版本控制
- 规则测试与验证
- 决策日志记录

### 核心功能

#### 5.1 规则评估
**接口**: `POST /api/v1/rules/evaluate`

**功能**: 执行规则引擎评估

**请求参数**:
```json
{
  "transactionId": "txn_001",
  "userId": "user_123",
  "features": {
    "amount": 1500.00,
    "txn_count_24h": 8,
    "merchant_risk_score": 0.15,
    // ... 更多特征
  },
  "ruleSetId": "production_v1",
  "ruleVersion": "2026-01-11"
}
```

**响应参数**:
```json
{
  "transactionId": "txn_001",
  "decision": "REVIEW",
  "score": 0.65,
  "triggeredRules": [
    {
      "ruleId": "high_amount",
      "ruleName": "High Amount Transaction",
      "action": "REVIEW",
      "scoreAdded": 0.3,
      "triggeredAt": 1736640001500,
      "details": {
        "actual_amount": 1500.00,
        "threshold": 1000.00
      }
    },
    {
      "ruleId": "high_frequency",
      "ruleName": "High Transaction Frequency",
      "action": "REVIEW",
      "scoreAdded": 0.35,
      "triggeredAt": 1736640001500,
      "details": {
        "actual_count": 8,
        "threshold": 5
      }
    }
  ],
  "evaluatedAt": 1736640001500
}
```

#### 5.2 规则列表查询
**接口**: `GET /api/v1/rules`

**查询参数**:
- `ruleSetId`: 规则集ID
- `status`: `ACTIVE` | `INACTIVE` | `ARCHIVED`
- `category`: 规则类别

**响应参数**:
```json
{
  "rules": [
    {
      "ruleId": "high_amount",
      "ruleName": "High Amount Transaction",
      "category": "AMOUNT",
      "status": "ACTIVE",
      "priority": 10,
      "description": "Flag transactions above threshold",
      "action": "REVIEW",
      "scoreAdded": 0.3,
      "version": "1.2",
      "updatedAt": 1736640000000
    },
    // ...
  ],
  "total": 25
}
```

#### 5.3 规则详情查询
**接口**: `GET /api/v1/rules/{ruleId}`

**响应参数**:
```json
{
  "ruleId": "high_amount",
  "ruleName": "High Amount Transaction",
  "category": "AMOUNT",
  "status": "ACTIVE",
  "priority": 10,
  "description": "Flag transactions above threshold",
  "action": "REVIEW",
  "scoreAdded": 0.3,
  "condition": {
    "type": "COMPARISON",
    "field": "amount",
    "operator": ">",
    "value": 1000.0
  },
  "version": "1.2",
  "createdAt": 1736000000000,
  "updatedAt": 1736640000000,
  "statistics": {
    "totalEvaluations": 50000,
    "triggeredCount": 5000,
    "triggerRate": 0.10,
    "precision": 0.85
  }
}
```

#### 5.4 规则创建/更新
**接口**: `POST /api/v1/rules` (创建)
**接口**: `PUT /api/v1/rules/{ruleId}` (更新)

**请求参数**:
```json
{
  "ruleId": "suspicious_merchant",
  "ruleName": "Suspicious Merchant Transaction",
  "category": "MERCHANT",
  "priority": 15,
  "description": "Flag transactions with high-risk merchants",
  "action": "REVIEW",
  "scoreAdded": 0.5,
  "condition": {
    "type": "COMPARISON",
    "field": "merchant_risk_score",
    "operator": ">=",
    "value": 0.7
  },
  "enabled": true
}
```

#### 5.5 规则集管理
**接口**: `POST /api/v1/rulesets`

**功能**: 创建规则集(规则分组)

**请求参数**:
```json
{
  "ruleSetId": "production_v2",
  "name": "Production Ruleset v2",
  "description": "Latest production rules",
  "ruleIds": [
    "blacklisted_user",
    "high_amount",
    "high_frequency",
    // ...
  ],
  "defaultAction": "APPROVE",
  "version": "2.0"
}
```

#### 5.6 规则测试
**接口**: `POST /api/v1/rules/test`

**功能**: 使用历史数据测试规则效果

**请求参数**:
```json
{
  "ruleId": "high_amount",
  "testData": {
    "startTime": 1736000000000,
    "endTime": 1736640000000,
    "sampleSize": 1000
  }
}
```

**响应参数**:
```json
{
  "ruleId": "high_amount",
  "testResults": {
    "totalSamples": 1000,
    "triggeredCount": 120,
    "triggerRate": 0.12,
    "precision": 0.82,  // TP / (TP + FP)
    "recall": 0.75,     // TP / (TP + FN)
    "f1Score": 0.78,
    "confusionMatrix": {
      "truePositive": 82,
      "falsePositive": 18,
      "trueNegative": 780,
      "falseNegative": 120
    }
  },
  "testedAt": 1736640000000
}
```

#### 5.7 决策日志查询
**接口**: `GET /api/v1/decisions/logs`

**查询参数**:
- `transactionId`: 交易ID
- `userId`: 用户ID
- `startTime`: 开始时间
- `endTime`: 结束时间
- `decision`: 决策类型

**响应参数**:
```json
{
  "logs": [
    {
      "transactionId": "txn_001",
      "userId": "user_123",
      "decision": "REVIEW",
      "score": 0.65,
      "triggeredRules": ["high_amount", "high_frequency"],
      "ruleSetId": "production_v1",
      "evaluatedAt": 1736640001500
    },
    // ...
  ],
  "total": 100
}
```

### 规则引擎实现建议

#### 规则DSL示例
```yaml
rules:
  - id: blacklisted_user
    name: Blacklisted User
    category: BLACKLIST
    priority: 100
    action: REJECT
    score_added: 1.0
    condition:
      type: IN_LIST
      field: user_id
      list_name: blacklist_users

  - id: high_amount
    name: High Amount Transaction
    category: AMOUNT
    priority: 10
    action: REVIEW
    score_added: 0.3
    condition:
      type: COMPARISON
      field: amount
      operator: ">"
      value: 1000.0

  - id: high_frequency_and_amount
    name: High Frequency and Amount
    category: BEHAVIOR
    priority: 20
    action: REVIEW
    score_added: 0.5
    condition:
      type: AND
      conditions:
        - type: COMPARISON
          field: txn_count_24h
          operator: ">"
          value: 5
        - type: COMPARISON
          field: amount
          operator: ">"
          value: 500.0
```

#### 引擎实现
```java
// 伪代码
public class RuleEngine {
  public RuleEvaluationResult evaluate(
    Map<String, Object> features,
    List<Rule> rules
  ) {
    double totalScore = 0.0;
    List<TriggeredRule> triggered = new ArrayList<>();

    // 按优先级排序
    rules.sort(Comparator.comparingInt(Rule::getPriority).reversed());

    for (Rule rule : rules) {
      if (rule.matches(features)) {
        totalScore += rule.getScoreAdded();
        triggered.add(new TriggeredRule(rule, features));

        // 可选: 短路逻辑
        if (rule.isStopOnMatch()) {
          break;
        }
      }
    }

    return new RuleEvaluationResult(totalScore, triggered);
  }
}
```

---

## 6. 跨服务接口设计

### 6.1 服务间通信
- **同步通信**: REST (HTTP/JSON)
- **高性能通信**: gRPC (Python模型)
- **异步通信**: Kafka (事件驱动)

### 6.2 API版本管理
- URL版本: `/api/v1/`, `/api/v2/`
- Header版本: `API-Version: 1.0`
- 向后兼容原则

### 6.3 通用响应格式
```json
{
  "success": true,
  "data": { ... },
  "error": {
    "code": "INVALID_PARAMETER",
    "message": "Invalid transaction ID format",
    "details": { ... }
  },
  "requestId": "req-uuid-001",
  "timestamp": 1736640000000
}
```

### 6.4 通用错误码
- `1000`: 参数校验失败
- `2000`: 资源不存在
- `3000`: 业务逻辑错误
- `4000`: 下游服务错误
- `5000`: 系统内部错误

---

## 7. 数据流设计

### 7.1 实时评估流程
```
1. 外部请求
   ↓
2. Gateway Service (认证、限流)
   ↓
3. Orchestrator Service
   ↓
4. 并行调用:
   - Feature Service (提取特征)
   - Transaction Service (查询历史)
   ↓
5. Decision Service (规则评估)
   ↓
6. Python ML Model (gRPC推理)
   ↓
7. 决策融合
   ↓
8. 返回结果 + 保存日志
```

### 7.2 异步事件流 (Kafka)
```
事件: TransactionCompleted
↓
消费者:
- Transaction Service: 更新统计、构建图谱
- Feature Service: 刷新缓存
- Monitoring: 更新指标
- Analytics: 离线分析
```

---

## 8. 性能指标

### 8.1 延迟要求
- **P50**: < 100ms
- **P95**: < 200ms
- **P99**: < 500ms

### 8.2 吞吐量
- 目标: 10,000 TPS
- 峰值: 20,000 TPS

### 8.3 可用性
- SLA: 99.9%
- 降级策略: 模型失败→规则失败→默认REVIEW

---

## 9. 安全设计

### 9.1 认证授权
- JWT Token认证
- API密钥管理
- 服务间mTLS

### 9.2 数据安全
- 敏感数据加密(PII)
- 传输加密(HTTPS/TLS)
- 审计日志

### 9.3 防护措施
- API限流
- DDoS防护
- 输入校验
- SQL注入防护

---

## 10. 监控与运维

### 10.1 监控指标
- **业务指标**:
  - 请求量、决策分布
  - 风险评分分布
  - 规则触发率

- **技术指标**:
  - 延迟(P50/P95/P99)
  - 错误率
  - 服务可用性

- **系统指标**:
  - CPU、内存、磁盘
  - 网络、数据库连接池

### 10.2 告警策略
- **P0**: 服务不可用
- **P1**: 错误率 > 5%
- **P2**: 延迟P99 > 1s
- **P3**: 异常模式检测

### 10.3 日志规范
- 结构化日志(JSON)
- TraceID全链路追踪
- 敏感信息脱敏

---

## 11. 实施优先级

### Phase 1: 基础功能 (已实现)
- ✅ Gateway Service
- ✅ Orchestrator Service (基础版)
- ✅ gRPC通信

### Phase 2: 核心服务
- 🔨 Transaction Service
  - 交易存储
  - 用户历史查询
- 🔨 Feature Service
  - 实时特征提取
  - 特征缓存
- 🔨 Decision Service
  - 规则引擎
  - 规则管理

### Phase 3: 高级功能
- 交易图谱构建
- 批量评估
- 特征工程优化
- 规则A/B测试

### Phase 4: 优化与扩展
- 性能优化
- 高可用部署
- 监控告警完善
- 机器学习模型迭代

---

## 12. 技术栈总结

| 层级 | 技术选型 |
|------|---------|
| 网关 | Spring Cloud Gateway |
| 应用框架 | Spring Boot 3.x |
| 数据库 | MySQL 8.0 |
| 缓存 | Redis 7.x |
| 图数据库 | Neo4j / NebulaGraph |
| 消息队列 | Apache Kafka |
| RPC | gRPC |
| 监控 | Prometheus + Grafana |
| 日志 | ELK Stack |
| 机器学习 | Python (PyTorch/TensorFlow) |

---

## 附录: 接口测试示例

### 示例1: 完整评估流程
```bash
curl -X POST http://localhost:8080/api/v1/evaluate \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "transactionId": "test_txn_001",
    "userId": "test_user_123",
    "eventTimestamp": 1736640000000,
    "amount": 1500.00,
    "currency": "USD",
    "productCd": "PAYMENT",
    "channel": "online",
    "attributes": {
      "merchantId": "merchant_456",
      "merchantCategory": "electronics"
    }
  }'
```

### 示例2: 批量评估
```bash
curl -X POST http://localhost:8080/api/v1/batch-evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "transactions": [
      { /* TransactionRequest 1 */ },
      { /* TransactionRequest 2 */ }
    ]
  }'
```

### 示例3: 查询用户历史
```bash
curl -X GET \
  "http://localhost:8083/api/v1/users/test_user_123/history?startTime=1736000000000&endTime=1736640000000"
```

---

**文档版本**: v1.0
**最后更新**: 2026-01-11
**维护者**: Risk Control Team
