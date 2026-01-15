# Gateway Service - 限流管理 API 使用说明

## 概述

已成功集成 Sentinel 流量防卫兵，并实现了限流规则管理端点。

## 新增端点

### 1. 查询所有限流规则
```bash
GET /api/v1/rate-limit/rules
```

**响应示例：**
```json
{
  "success": true,
  "count": 2,
  "rules": [
    {
      "resource": "/api/v1/transactions",
      "limitApp": "default",
      "grade": 1,
      "count": 100,
      "strategy": 0,
      "controlBehavior": 0,
      "warmUpPeriodSec": 10,
      "timeoutInSec": 0,
      "clusterMode": false
    }
  ]
}
```

### 2. 查询特定资源的限流规则
```bash
GET /api/v1/rate-limit/rules/{resource}
```

**示例：**
```bash
curl http://localhost:8080/api/v1/rate-limit/rules/api%2Fv1%2Ftransactions
```

### 3. 创建或更新限流规则
```bash
POST /api/v1/rate-limit/rules
Content-Type: application/json
```

**请求体参数说明：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| resource | String | 是 | 资源名称（API端点路径），如 `/api/v1/transactions` |
| limitApp | String | 否 | 限制应用，默认 `default`（所有应用） |
| grade | Integer | 是 | 限流阈值类型（0: 线程数, 1: QPS），默认 `1` |
| count | Long | 是 | 阈值（QPS: 每秒请求数, 线程数: 并发线程数） |
| strategy | Integer | 否 | 策略（0: 直接拒绝, 1: Warm Up, 2: 匀速排队），默认 `0` |
| controlBehavior | Integer | 否 | 控制行为（0: 拒绝, 1: 预热, 2: 排队），默认 `0` |
| warmUpPeriodSec | Integer | 否 | 预热时长（秒），默认 `10` |
| timeoutInSec | Integer | 否 | 超时时长（秒），默认 `0` |
| clusterMode | Boolean | 否 | 集群模式，默认 `false` |
| description | String | 否 | 规则描述 |

**示例 1：限制交易端点为 200 QPS**
```bash
curl -X POST http://localhost:8080/api/v1/rate-limit/rules \
  -H "Content-Type: application/json" \
  -d '{
    "resource": "/api/v1/transactions",
    "grade": 1,
    "count": 200,
    "strategy": 0,
    "controlBehavior": 0,
    "description": "Limit transactions to 200 QPS"
  }'
```

**示例 2：使用预热策略**
```bash
curl -X POST http://localhost:8080/api/v1/rate-limit/rules \
  -H "Content-Type: application/json" \
  -d '{
    "resource": "/api/v1/transactions",
    "grade": 1,
    "count": 500,
    "strategy": 1,
    "controlBehavior": 1,
    "warmUpPeriodSec": 20,
    "description": "Warm up to 500 QPS over 20 seconds"
  }'
```

**示例 3：使用匀速排队策略**
```bash
curl -X POST http://localhost:8080/api/v1/rate-limit/rules \
  -H "Content-Type: application/json" \
  -d '{
    "resource": "/api/v1/transactions",
    "grade": 1,
    "count": 100,
    "strategy": 2,
    "controlBehavior": 2,
    "timeoutInSec": 5,
    "description": "Queue requests, max wait 5 seconds"
  }'
```

**响应示例：**
```json
{
  "success": true,
  "message": "Rule created/updated successfully",
  "rule": {
    "resource": "/api/v1/transactions",
    "grade": 1,
    "count": 200,
    "strategy": 0,
    "controlBehavior": 0,
    "description": "Limit transactions to 200 QPS"
  },
  "totalRules": 2
}
```

### 4. 删除限流规则
```bash
DELETE /api/v1/rate-limit/rules/{resource}
```

**示例：**
```bash
curl -X DELETE http://localhost:8080/api/v1/rate-limit/rules/api%2Fv1%2Ftransactions
```

## 限流策略说明

### 1. 直接拒绝（Default）
- `strategy`: 0
- `controlBehavior`: 0
- **行为**：超过阈值直接拒绝请求
- **适用场景**：刚性限流，保护系统

### 2. 预热模式（Warm Up）
- `strategy`: 1
- `controlBehavior`: 1
- **行为**：冷启动时阈值较小，逐步增加到设定值
- **参数**：`warmUpPeriodSec`（预热时长）
- **适用场景**：秒杀活动、缓存预热

### 3. 匀速排队（Rate Limiter）
- `strategy`: 2
- `controlBehavior`: 2
- **行为**：请求排队，匀速通过
- **参数**：`timeoutInSec`（超时时间）
- **适用场景**：削峰填谷

## Sentinel Dashboard

配置了 Sentinel Dashboard 用于可视化监控：
- **地址**: http://localhost:8858
- **端口**: 8719（API 端口）

### 启动 Dashboard
```bash
# 下载 Sentinel Dashboard
wget https://github.com/alibaba/Sentinel/releases/download/1.8.6/sentinel-dashboard-1.8.6.jar

# 启动 Dashboard
java -Dserver.port=8858 -Dcsp.sentinel.dashboard.server=localhost:8858 -Dproject.name=gateway-service -jar sentinel-dashboard-1.8.6.jar
```

## 规则持久化

规则会自动保存到 `sentinel-rules.json` 文件，服务重启后自动加载。

### 规则文件位置
```
services/gateway-service/src/main/resources/sentinel-rules.json
```

## 测试限流效果

### 使用 Apache Bench 测试
```bash
# 测试 1000 个请求，并发 50
ab -n 1000 -c 50 -p transaction.json -T application/json http://localhost:8080/api/v1/transactions
```

### 使用 curl 测试
```bash
# 快速发送多个请求
for i in {1..150}; do
  curl -X POST http://localhost:8080/api/v1/transactions \
    -H "Content-Type: application/json" \
    -d '{"transactionId":"txn'$i'","userId":"user001","transactionAmt":100.00}' &
done
wait
```

## 改进总结

### 已完成的改进
1. ✅ 集成 Sentinel 流量防卫兵
2. ✅ 实现限流规则管理端点（GET/POST/DELETE）
3. ✅ 删除冗余的 `/api/v1/health` 和 `/api/v1/info` 端点
4. ✅ 保护 Actuator 端点（仅暴露 `health`）
5. ✅ 规则持久化到 JSON 文件
6. ✅ 支持动态修改限流规则（无需重启服务）

### 新增文件
- `RateLimitRule.java` - 限流规则模型
- `RateLimitService.java` - 限流规则管理服务
- `RateLimitController.java` - 限流规则管理控制器
- `SentinelConfig.java` - Sentinel 配置类
- `sentinel-rules.json` - 限流规则持久化文件

### 修改文件
- `pom.xml` - 添加 Sentinel 依赖
- `application.yml` - 添加 Sentinel 配置
- `GatewayController.java` - 删除冗余端点

## 下一步建议

1. **集成 Sentinel Dashboard** - 可视化监控
2. **使用 Nacos 持久化规则** - 支持分布式规则管理
3. **添加熔断降级规则** - 保护后端服务
4. **实现系统自适应保护** - 根据 CPU/RT 自动调整
5. **添加认证鉴权** - 保护限流管理端点

## 参考资料

- [Sentinel 官方文档](https://sentinelguard.io/zh-cn/docs/)
- [Spring Cloud Alibaba Sentinel](https://github.com/alibaba/spring-cloud-alibaba/wiki/Sentinel)
- [REFERENCE_PROJECT.md](../../docs/REFERENCE_PROJECT.md) - 项目参考文档
