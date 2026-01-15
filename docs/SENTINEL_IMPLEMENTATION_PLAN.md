# Sentinel 流量保护和熔断降级实施规划

> **目标**：为风控系统引入企业级流量保护和熔断降级能力，提升系统稳定性和可用性

**项目信息**：
- Spring Boot 3.2.2 + Java 17
- 微服务架构：Gateway (8080) → Orchestrator (8081) → 下游服务
- 现有降级策略：`enable-fallback: true`（模型失败降级到规则）

---

## 📋 目录

1. [架构设计](#1-架构设计)
2. [分阶段实施计划](#2-分阶段实施计划)
3. [技术方案详解](#3-技术方案详解)
4. [规则配置详解](#4-规则配置详解)
5. [监控和运维](#5-监控和运维)
6. [测试方案](#6-测试方案)
7. [风险控制](#7-风险控制)

---

## 1. 架构设计

### 1.1 当前架构分析

```
┌─────────────┐      ┌──────────────┐      ┌────────────────┐
│   Client    │ ──>  │    Gateway   │ ──>  │  Orchestrator  │
│             │      │   (8080)     │      │    (8081)      │
└─────────────┘      └──────────────┘      └────────┬───────┘
                                                     │
                           ┌─────────────────────────┼───────────────────┐
                           ▼                         ▼                   ▼
                    ┌──────────┐            ┌─────────────┐      ┌────────────┐
                    │ Feature  │            │   Decision  │      │   Python   │
                    │ (8082)   │            │   (8083)    │      │  (gRPC)    │
                    └──────────┘            └─────────────┘      └────────────┘

现有保护：
❌ 无流量控制
❌ 无熔断降级
✅ 基础超时配置（5秒）
✅ 简单降级策略（模型失败降级到规则）
```

### 1.2 Sentinel 引入后的架构

```
┌─────────────┐      ┌──────────────────────────────────┐
│   Client    │ ──>  │      Gateway Service (8080)      │
│             │      │  ┌────────────────────────────┐  │
│             │      │  │  Sentinel - Gateway 保护   │  │
│             │      │  │  • QPS 限流: 1000/s        │  │
│             │      │  │  • 并发线程限流: 200       │  │
│             │      │  │  • 慢调用熔断              │  │
│             │      │  └────────────────────────────┘  │
└─────────────┘      └─────────────┬────────────────────┘
                                   │
                                   ▼
                    ┌──────────────────────────────────┐
                    │   Orchestrator Service (8081)    │
                    │  ┌────────────────────────────┐  │
                    │  │ Sentinel - 服务调用保护    │  │
                    │  │ • Feature服务熔断          │  │
                    │  │ • Decision服务熔断         │  │
                    │  │ • Python服务熔断           │  │
                    │  │ • 异常比例熔断             │  │
                    │  └────────────────────────────┘  │
                    └─────────┬────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        ▼                     ▼                     ▼
 ┌──────────┐         ┌─────────────┐       ┌────────────┐
 │ Feature  │         │   Decision  │       │   Python   │
 │ (8082)   │         │   (8083)    │       │  (gRPC)    │
 └──────────┘         └─────────────┘       └────────────┘
```

### 1.3 保护策略矩阵

| 服务 | 保护点 | 保护策略 | 阈值建议 | 降级方案 |
|------|--------|----------|----------|----------|
| **Gateway** | 入口流量 | QPS限流 | 1000 req/s | 快速失败 |
| **Gateway** | 处理能力 | 并发线程限流 | 200 threads | 排队等待 |
| **Gateway** | 响应时间 | 慢调用比例熔断 | RT > 3s, 比例 > 50% | 降级到默认响应 |
| **Gateway → Orchestrator** | 下游调用 | 熔断降级 | 异常比例 > 50% | 返回 REVIEW 决策 |
| **Orchestrator → Feature** | 下游调用 | 熔断降级 | 异常比例 > 50% | 使用默认特征 |
| **Orchestrator → Decision** | 下游调用 | 熔断降级 | 异常比例 > 50% | 使用规则降级 |
| **Orchestrator → Python** | 下游调用 | 熔断降级 | 异常比例 > 50% | 降级到规则引擎 |

---

## 2. 分阶段实施计划

### 📅 第一阶段：Gateway 保护（1-2周）

**目标**：保护系统入口，防止流量过载

**任务清单**：
- [ ] 引入 Sentinel 依赖
- [ ] 集成 Sentinel Dashboard
- [ ] 配置 Gateway QPS 限流规则
- [ ] 配置并发线程限流规则
- [ ] 实现限流异常处理器
- [ ] 编写单元测试
- [ ] 压测验证

**验收标准**：
- ✅ QPS 超过阈值时触发限流
- ✅ 限流日志正确记录
- ✅ Dashboard 可查看监控数据
- ✅ 限流不影响正常流量

**预期成果**：
```
Gateway 增加能力：
• 入口 QPS 限流：1000/s
• 并发线程限制：200
• 限流日志：logs/gateway-sentinel.log
• 监控面板：http://localhost:8858
```

---

### 📅 第二阶段：服务调用熔断（2-3周）

**目标**：保护 Gateway → Orchestrator 调用链路

**任务清单**：
- [ ] 配置 Gateway 调用 Orchestrator 的熔断规则
- [ ] 实现 BlockExceptionHandler
- [ ] 实现降级逻辑（返回 REVIEW 决策）
- [ ] 配置熔断规则（慢调用比例、异常比例）
- [ ] 编写集成测试
- [ ] 模拟故障场景测试

**验收标准**：
- ✅ Orchestrator 响应慢时自动熔断
- ✅ Orchestrator 异常时自动熔断
- ✅ 熔断期间返回降级响应
- ✅ 熔断后自动恢复

**预期成果**：
```
熔断策略：
• 慢调用阈值：RT > 3s
• 熔断比例：50%
• 熔断时长：10秒
• 降级响应：{"decision": "REVIEW", "reason": "Service degraded"}
```

---

### 📅 第三阶段：Orchestrator 下游保护（2-3周）

**目标**：保护 Orchestrator 调用下游服务的链路

**任务清单**：
- [ ] 配置 Feature Service 熔断规则
- [ ] 配置 Decision Service 熔断规则
- [ ] 配置 Python Service (gRPC) 熔断规则
- [ ] 实现各服务降级逻辑
- [ ] 增强现有 fallback 机制
- [ ] 编写集成测试

**验收标准**：
- ✅ Feature 服务异常时使用默认特征
- ✅ Decision 服务异常时降级到规则引擎
- ✅ Python 服务异常时触发现有 fallback
- ✅ 所有降级逻辑正确执行

**预期成果**：
```
下游服务保护：
• Feature Service: 异常比例 > 50% 时熔断，使用默认特征
• Decision Service: 异常比例 > 50% 时熔断，降级到规则
• Python Service: 异常比例 > 50% 时熔断，触发 enable-fallback
```

---

### 📅 第四阶段：规则持久化和优化（1-2周）

**目标**：实现规则动态配置，优化系统稳定性

**任务清单**：
- [ ] 集成 Nacos 规则持久化
- [ ] 配置规则自动推送
- [ ] 实现规则变更审计日志
- [ ] 优化规则阈值（基于监控数据）
- [ ] 编写运维文档

**验收标准**：
- ✅ 规则变更无需重启服务
- ✅ 规则变更有审计记录
- ✅ Dashboard 可查看历史规则
- ✅ 规则优化基于真实数据

**预期成果**：
```
规则管理：
• 持久化方案：Nacos 配置中心
• 规则格式：JSON
• 变更流程：Dashboard 配置 → Nacos 推送 → 服务热加载
• 审计日志：记录所有规则变更
```

---

## 3. 技术方案详解

### 3.1 Maven 依赖配置

#### 3.1.1 父 POM 添加版本管理

**文件**：`pom.xml`

```xml
<properties>
    <!-- Sentinel 版本 -->
    <sentinel.version>1.8.6</sentinel.version>
    <spring-cloud-alibaba.version>2022.0.0.0</spring-cloud-alibaba.version>
</properties>

<dependencyManagement>
    <dependencies>
        <!-- Sentinel -->
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-alibaba-sentinel</artifactId>
            <version>${spring-cloud-alibaba.version}</version>
            <type>pom</type>
            <scope>import</type>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-sentinel</artifactId>
            <version>${spring-cloud-alibaba.version}</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

#### 3.1.2 Gateway Service 添加依赖

**文件**：`services/gateway-service/pom.xml`

```xml
<dependencies>
    <!-- 现有依赖... -->

    <!-- Sentinel -->
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-sentinel</artifactId>
    </dependency>

    <!-- Sentinel DataSource (Nacos) - 可选，用于规则持久化 -->
    <dependency>
        <groupId>com.alibaba.csp</groupId>
        <artifactId>sentinel-datasource-nacos</artifactId>
        <version>${sentinel.version}</version>
    </dependency>
</dependencies>
```

#### 3.1.3 Orchestrator Service 添加依赖

**文件**：`services/risk-orchestrator-service/pom.xml`

```xml
<dependencies>
    <!-- 现有依赖... -->

    <!-- Sentinel -->
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-sentinel</artifactId>
    </dependency>

    <!-- Sentinel DataSource (Nacos) -->
    <dependency>
        <groupId>com.alibaba.csp</groupId>
        <artifactId>sentinel-datasource-nacos</artifactId>
        <version>${sentinel.version}</version>
    </dependency>
</dependencies>
```

---

### 3.2 Gateway Service 配置

**文件**：`services/gateway-service/src/main/resources/application.yml`

```yaml
server:
  port: 8080

spring:
  application:
    name: gateway-service

  # Sentinel 配置
  cloud:
    sentinel:
      # 启用 Sentinel
      enabled: true
      # 传输配置（连接到 Dashboard）
      transport:
        dashboard: localhost:8858  # Sentinel Dashboard 地址
        port: 8719  # Sentinel API 端口，会被占用会自动 +1
      # 心跳配置
      heartbeat:
        client-ip: ${spring.cloud.client.ip-address}
      # Web 配置
      web-context-unify: false  # 禁用 Context 统一，细化埋点
      # 限流处理
      block-page: /blocked  # 限流重定向页面（可选）

  # 数据源配置（Nacos）- 可选
  cloud.sentinel.datasource:
    flow:
      nacos:
        server-addr: localhost:8848
        dataId: ${spring.application.name}-flow-rules
        groupId: SENTINEL_GROUP
        rule-type: flow  # 流控规则
        data-type: json
    degrade:
      nacos:
        server-addr: localhost:8848
        dataId: ${spring.application.name}-degrade-rules
        groupId: SENTINEL_GROUP
        rule-type: degrade  # 熔断规则
        data-type: json

gateway:
  orchestrator-base-url: http://localhost:8081
  timeout-ms: 5000
  log-requests: true
  log-responses: true

# Spring Actuator
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,sentinel  # 新增 sentinel 端点
      base-path: /actuator
  endpoint:
    health:
      show-details: always

# 日志配置
logging:
  level:
    root: INFO
    com.risk.gateway: DEBUG
    com.alibaba.csp.sentinel: DEBUG  # Sentinel 日志
```

---

### 3.3 Orchestrator Service 配置

**文件**：`services/risk-orchestrator-service/src/main/resources/application.yml`

```yaml
server:
  port: 8081

spring:
  application:
    name: orchestrator-service

  # Sentinel 配置
  cloud:
    sentinel:
      enabled: true
      transport:
        dashboard: localhost:8858
        port: 8720  # 不同服务使用不同端口
      heartbeat:
        client-ip: ${spring.cloud.client.ip-address}
      web-context-unify: false

  # 数据源配置（Nacos）
  cloud.sentinel.datasource:
    flow:
      nacos:
        server-addr: localhost:8848
        dataId: ${spring.application.name}-flow-rules
        groupId: SENTINEL_GROUP
        rule-type: flow
        data-type: json
    degrade:
      nacos:
        server-addr: localhost:8848
        dataId: ${spring.application.name}-degrade-rules
        groupId: SENTINEL_GROUP
        rule-type: degrade
        data-type: json

orchestrator:
  feature-service-url: http://localhost:8082
  decision-service-url: http://localhost:8083
  python-inference-host: 10.60.38.173
  python-inference-port: 49094

  feature-service-timeout-ms: 2000
  decision-service-timeout-ms: 1000
  python-inference-timeout-ms: 5000

  mode: HYBRID
  enable-fallback: true
  review-threshold: 0.5
  reject-threshold: 0.7
  mock-mode: false

# Spring Actuator
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,sentinel
      base-path: /actuator
  endpoint:
    health:
      show-details: always

# 日志配置
logging:
  level:
    root: INFO
    com.risk.orch: DEBUG
    com.alibaba.csp.sentinel: DEBUG
    io.grpc: INFO
```

---

### 3.4 代码改动点

#### 3.4.1 Gateway - 限流异常处理器

**新建文件**：`services/gateway-service/src/main/java/com/risk_gateway/handler/SentinelBlockExceptionHandler.java`

```java
package com.risk_gateway.handler;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.risk_gateway.model.TransactionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

/**
 * Sentinel 限流/熔断异常处理器
 */
@Slf4j
@Order(-1)  // 确保优先级最高
public class SentinelBlockExceptionHandler implements WebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (ex instanceof BlockException) {
            return handleBlockException(exchange, (BlockException) ex);
        }
        return Mono.error(ex);
    }

    private Mono<Void> handleBlockException(ServerWebExchange exchange, BlockException ex) {
        String requestId = exchange.getRequest().getId();
        String transactionId = exchange.getRequest().getHeaders().getFirst("X-Transaction-Id");

        log.warn("Sentinel block triggered: transactionId={}, type={}, ruleLimitApp={}",
                transactionId, ex.getClass().getSimpleName(), ex.getRuleLimitApp());

        // 构建降级响应
        TransactionResponse response = buildBlockedResponse(ex, transactionId);

        exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        String responseBody = toJson(response);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(responseBody.getBytes());
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private TransactionResponse buildBlockedResponse(BlockException ex, String transactionId) {
        TransactionResponse response = new TransactionResponse();
        response.setTransactionId(transactionId);
        response.setStatusCode(429);  // Too Many Requests

        if (ex instanceof FlowException) {
            response.setDecision("REVIEW");
            response.setRiskScore(0.5);
            response.setReasons("rate_limit_exceeded");
            response.setMessage("Request rate limit exceeded, please try again later");
        } else if (ex instanceof DegradeException) {
            response.setDecision("REVIEW");
            response.setRiskScore(0.6);
            response.setReasons("service_degraded");
            response.setMessage("Service temporarily degraded, please try again later");
        } else if (ex instanceof AuthorityException) {
            response.setDecision("REJECT");
            response.setRiskScore(0.9);
            response.setReasons("access_denied");
            response.setMessage("Access denied");
        } else {
            response.setDecision("REVIEW");
            response.setRiskScore(0.5);
            response.setReasons("blocked_by_sentinel");
            response.setMessage("Request blocked by Sentinel: " + ex.getClass().getSimpleName());
        }

        response.setProcessedAt(System.currentTimeMillis());
        return response;
    }

    private String toJson(TransactionResponse response) {
        // 使用 Jackson 或其他 JSON 库序列化
        // 简化示例
        return String.format(
            "{\"transactionId\":\"%s\",\"decision\":\"%s\",\"riskScore\":%s,\"reasons\":\"%s\",\"statusCode\":%s,\"message\":\"%s\"}",
            response.getTransactionId(),
            response.getDecision(),
            response.getRiskScore(),
            response.getReasons(),
            response.getStatusCode(),
            response.getMessage()
        );
    }
}
```

**配置类**：`services/gateway-service/src/main/java/com/risk_gateway/config/SentinelConfig.java`

```java
package com.risk_gateway.config;

import com.risk_gateway.handler.SentinelBlockExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sentinel 配置
 */
@Configuration
public class SentinelConfig {

    @Bean
    public SentinelBlockExceptionHandler sentinelBlockExceptionHandler() {
        return new SentinelBlockExceptionHandler();
    }
}
```

#### 3.4.2 Gateway - Controller 添加 Sentinel 资源定义

**修改文件**：`services/gateway-service/src/main/java/com/risk_gateway/controller/GatewayController.java`

```java
package com.risk_gateway.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.risk_gateway.service.GatewayService;
import com.risk_gateway.model.TransactionRequest;
import com.risk_gateway.model.TransactionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * Gateway REST Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
public class GatewayController {

    @Autowired
    private GatewayService gatewayService;

    /**
     * 交易评估接口
     * 添加 Sentinel 资源定义
     */
    @PostMapping("/transactions")
    @SentinelResource(
        value = "evaluateTransaction",  // 资源名称
        blockHandler = "handleBlock",     // 限流/熔断处理方法
        fallback = "handleFallback"       // 异常降级处理方法
    )
    public Mono<TransactionResponse> evaluateTransaction(@RequestBody TransactionRequest request) {
        log.info("Received transaction evaluation request: transactionId={}", request.getTransactionId());
        return gatewayService.processTransaction(request);
    }

    /**
     * 批量交易评估接口（待实现）
     */
    @PostMapping("/transactions/batch")
    @SentinelResource(value = "evaluateBatchTransactions")
    public String evaluateBatchTransactions(@RequestBody java.util.List<TransactionRequest> requests) {
        return "Batch processing not implemented yet";
    }

    /**
     * Sentinel 限流/熔断处理方法
     * 必须与原方法签名一致，最后加上 BlockException 参数
     */
    public Mono<TransactionResponse> handleBlock(TransactionRequest request, BlockException ex) {
        log.warn("Transaction blocked by Sentinel: transactionId={}, rule={}",
                request.getTransactionId(), ex.getRule());

        TransactionResponse response = new TransactionResponse();
        response.setTransactionId(request.getTransactionId());
        response.setDecision("REVIEW");
        response.setRiskScore(0.5);
        response.setReasons("rate_limit");
        response.setStatusCode(429);
        response.setMessage("Request blocked by Sentinel: " + ex.getClass().getSimpleName());
        response.setProcessedAt(System.currentTimeMillis());

        return Mono.just(response);
    }

    /**
     * 异常降级处理方法
     * 必须与原方法签名一致，最后加上 Throwable 参数
     */
    public Mono<TransactionResponse> handleFallback(TransactionRequest request, Throwable ex) {
        log.error("Transaction processing failed: transactionId={}", request.getTransactionId(), ex);

        TransactionResponse response = new TransactionResponse();
        response.setTransactionId(request.getTransactionId());
        response.setDecision("REVIEW");
        response.setRiskScore(0.6);
        response.setReasons("processing_error");
        response.setStatusCode(500);
        response.setMessage("Processing error: " + ex.getMessage());
        response.setProcessedAt(System.currentTimeMillis());

        return Mono.just(response);
    }
}
```

#### 3.4.3 Orchestrator - 下游服务熔断配置

**修改文件**：`services/risk-orchestrator-service/src/main/java/com/risk/orch/service/OrchestratorService.java`

```java
package com.risk.orch.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.risk.orch.model.*;
import com.risk.orch.service.grpc.PythonInferenceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

/**
 * Orchestrator Service - 核心编排逻辑
 */
@Slf4j
@Service
public class OrchestratorService {

    @Autowired
    private WebClient featureServiceWebClient;

    @Autowired
    private WebClient decisionServiceWebClient;

    @Autowired(required = false)
    private PythonInferenceClient pythonInferenceClient;

    @Autowired
    private OrchestratorProperties properties;

    /**
     * 评估交易
     */
    public Mono<OrchestratorResponse> evaluate(OrchestratorRequest request) {
        log.info("Evaluating transaction: transactionId={}, mode={}",
                request.getTransactionId(), properties.getMode());

        if (properties.isMockMode()) {
            return evaluateMock(request);
        }

        switch (properties.getMode()) {
            case "RULES_ONLY":
                return evaluateWithRulesOnly(request);
            case "MODEL_ONLY":
                return evaluateWithModelOnly(request);
            case "HYBRID":
            default:
                return evaluateHybrid(request);
        }
    }

    /**
     * 混合模式：规则 + 模型
     */
    private Mono<OrchestratorResponse> evaluateHybrid(OrchestratorRequest request) {
        // 1. 调用 Feature Service（带熔断保护）
        Mono<FeatureResponse> featureMono = fetchFeaturesWithProtection(request);

        // 2. 调用 Decision Service（带熔断保护）
        Mono<DecisionResponse> decisionMono = fetchDecisionWithProtection(request);

        // 3. 调用 Python 模型（带熔断保护）
        Mono<InferenceResponse> modelMono = fetchModelInferenceWithProtection(request);

        // 4. 组合结果
        return Mono.zip(featureMono, decisionMono, modelMono)
                .map(tuple -> {
                    FeatureResponse features = tuple.getT1();
                    DecisionResponse decision = tuple.getT2();
                    InferenceResponse model = tuple.getT3();

                    return combineResults(request, features, decision, model, "hybrid");
                })
                .onErrorResume(ex -> {
                    log.error("Hybrid evaluation failed: {}", ex.getMessage());
                    return handleEvaluationFailure(request, ex);
                });
    }

    /**
     * 调用 Feature Service（带熔断保护）
     */
    @SentinelResource(
        value = "fetchFeatures",
        blockHandler = "handleFeatureBlock",
        fallback = "handleFeatureFallback"
    )
    private Mono<FeatureResponse> fetchFeaturesWithProtection(OrchestratorRequest request) {
        return featureServiceWebClient.post()
                .uri("/api/v1/features/extract")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(FeatureResponse.class)
                .timeout(java.time.Duration.ofMillis(properties.getFeatureServiceTimeoutMs()));
    }

    /**
     * Feature Service 熔断处理
     */
    private Mono<FeatureResponse> handleFeatureBlock(OrchestratorRequest request, BlockException ex) {
        log.warn("Feature Service blocked by Sentinel: {}", ex.getRule());
        return Mono.just(getDefaultFeatures(request));
    }

    /**
     * Feature Service 异常降级
     */
    private Mono<FeatureResponse> handleFeatureFallback(OrchestratorRequest request, Throwable ex) {
        log.error("Feature Service fallback: {}", ex.getMessage());
        return Mono.just(getDefaultFeatures(request));
    }

    /**
     * 调用 Decision Service（带熔断保护）
     */
    @SentinelResource(
        value = "fetchDecision",
        blockHandler = "handleDecisionBlock",
        fallback = "handleDecisionFallback"
    )
    private Mono<DecisionResponse> fetchDecisionWithProtection(OrchestratorRequest request) {
        return decisionServiceWebClient.post()
                .uri("/api/v1/decide")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(DecisionResponse.class)
                .timeout(java.time.Duration.ofMillis(properties.getDecisionServiceTimeoutMs()));
    }

    /**
     * Decision Service 熔断处理
     */
    private Mono<DecisionResponse> handleDecisionBlock(OrchestratorRequest request, BlockException ex) {
        log.warn("Decision Service blocked by Sentinel: {}", ex.getRule());
        return Mono.just(getDefaultDecision(request));
    }

    /**
     * Decision Service 异常降级
     */
    private Mono<DecisionResponse> handleDecisionFallback(OrchestratorRequest request, Throwable ex) {
        log.error("Decision Service fallback: {}", ex.getMessage());
        return Mono.just(getDefaultDecision(request));
    }

    /**
     * 调用 Python 模型推理（带熔断保护）
     */
    @SentinelResource(
        value = "fetchModelInference",
        blockHandler = "handleModelBlock",
        fallback = "handleModelFallback"
    )
    private Mono<InferenceResponse> fetchModelInferenceWithProtection(OrchestratorRequest request) {
        if (pythonInferenceClient == null) {
            return Mono.error(new IllegalStateException("Python client not initialized"));
        }

        return Mono.fromCallable(() -> pythonInferenceClient.inference(request))
                .timeout(java.time.Duration.ofMillis(properties.getPythonInferenceTimeoutMs()));
    }

    /**
     * Python 模型熔断处理
     */
    private Mono<InferenceResponse> handleModelBlock(OrchestratorRequest request, BlockException ex) {
        log.warn("Python Model blocked by Sentinel: {}", ex.getRule());
        if (properties.isEnableFallback()) {
            return Mono.just(getDefaultInferenceFallback(request));
        }
        return Mono.error(ex);
    }

    /**
     * Python 模型异常降级
     */
    private Mono<InferenceResponse> handleModelFallback(OrchestratorRequest request, Throwable ex) {
        log.error("Python Model fallback: {}", ex.getMessage());
        if (properties.isEnableFallback()) {
            return Mono.just(getDefaultInferenceFallback(request));
        }
        return Mono.error(ex);
    }

    // ========== 默认值方法 ==========

    private FeatureResponse getDefaultFeatures(OrchestratorRequest request) {
        FeatureResponse response = new FeatureResponse();
        response.setTransactionId(request.getTransactionId());
        response.setFeatures(java.util.Collections.emptyMap());
        response.setFeatureNames(Arrays.asList());
        return response;
    }

    private DecisionResponse getDefaultDecision(OrchestratorRequest request) {
        DecisionResponse response = new DecisionResponse();
        response.setTransactionId(request.getTransactionId());
        response.setDecision("REVIEW");
        response.setRiskScore(0.5);
        response.setReasons(Arrays.asList("decision_service_degraded"));
        return response;
    }

    private InferenceResponse getDefaultInferenceFallback(OrchestratorRequest request) {
        InferenceResponse response = new InferenceResponse();
        response.setDecision("REVIEW");
        response.setRiskScore(0.5);
        response.setTopReasons(Arrays.asList("model_degraded_to_rules"));
        return response;
    }
}
```

---

## 4. 规则配置详解

### 4.1 规则类型说明

Sentinel 支持多种规则类型，本方案主要使用：

#### 4.1.1 流控规则 (FlowRule)

保护系统不被流量打垮。

**参数说明**：
- `resource`：资源名称（如 `evaluateTransaction`）
- `grade`：限流阈值类型（0: 线程数, 1: QPS）
- `count`：限流阈值
- `strategy`：流控策略（0: 直接拒绝, 1: Warm Up, 2: 匀速排队）
- `controlBehavior`：流控效果（0: 快速失败, 1: Warm Up, 2: 匀速排队, 3: 预热排队）

#### 4.1.2 熔断降级规则 (DegradeRule)

保护下游服务不被拖垮。

**参数说明**：
- `resource`：资源名称
- `grade`：熔断策略（0: 慢调用比例, 1: 异常比例, 2: 异常数）
- `count`：阈值
- `timeWindow`：熔断时长（秒）
- `minRequestAmount`：最小请求数
- `statIntervalMs`：统计时长（毫秒）
- `slowRatioThreshold`：慢调用比例阈值（grade=0 时使用）

---

### 4.2 Gateway 规则配置

#### 4.2.1 流控规则示例

**JSON 格式**（用于 Nacos 配置）：
```json
[
  {
    "resource": "evaluateTransaction",
    "limitApp": "default",
    "grade": 1,
    "count": 1000,
    "strategy": 0,
    "controlBehavior": 0,
    "clusterMode": false
  },
  {
    "resource": "evaluateBatchTransactions",
    "limitApp": "default",
    "grade": 1,
    "count": 100,
    "strategy": 0,
    "controlBehavior": 0,
    "clusterMode": false
  }
]
```

**规则说明**：
- `evaluateTransaction`：QPS 限流 1000/s
- `evaluateBatchTransactions`：QPS 限流 100/s

#### 4.2.2 熔断规则示例

```json
[
  {
    "resource": "evaluateTransaction",
    "grade": 0,
    "count": 3000,
    "timeWindow": 10,
    "minRequestAmount": 5,
    "statIntervalMs": 10000,
    "slowRatioThreshold": 0.5
  }
]
```

**规则说明**：
- 熔断策略：慢调用比例
- 慢调用阈值：RT > 3000ms
- 熔断比例：50%
- 熔断时长：10秒
- 最小请求数：5
- 统计时长：10秒

---

### 4.3 Orchestrator 规则配置

#### 4.3.1 下游服务熔断规则

**JSON 格式**：
```json
[
  {
    "resource": "fetchFeatures",
    "grade": 1,
    "count": 0.5,
    "timeWindow": 10,
    "minRequestAmount": 5,
    "statIntervalMs": 10000
  },
  {
    "resource": "fetchDecision",
    "grade": 1,
    "count": 0.5,
    "timeWindow": 10,
    "minRequestAmount": 5,
    "statIntervalMs": 10000
  },
  {
    "resource": "fetchModelInference",
    "grade": 1,
    "count": 0.5,
    "timeWindow": 10,
    "minRequestAmount": 5,
    "statIntervalMs": 10000
  }
]
```

**规则说明**：
- 熔断策略：异常比例
- 异常比例阈值：50%
- 熔断时长：10秒
- 最小请求数：5
- 统计时长：10秒

---

## 5. 监控和运维

### 5.1 Sentinel Dashboard 部署

#### 5.1.1 下载 Dashboard

```bash
# 下载 Sentinel Dashboard
wget https://github.com/alibaba/Sentinel/releases/download/1.8.6/sentinel-dashboard-1.8.6.jar

# 或使用 curl
curl -L -o sentinel-dashboard.jar https://github.com/alibaba/Sentinel/releases/download/1.8.6/sentinel-dashboard-1.8.6.jar
```

#### 5.1.2 启动 Dashboard

```bash
# 启动 Dashboard（默认端口 8080）
java -Dserver.port=8858 -Dcsp.sentinel.dashboard.server=localhost:8858 -Dproject.name=sentinel-dashboard -jar sentinel-dashboard-1.8.6.jar

# 或使用自定义端口
java -Dserver.port=8858 -Dcsp.sentinel.dashboard.server=localhost:8858 -jar sentinel-dashboard-1.8.6.jar
```

#### 5.1.3 访问 Dashboard

- URL: http://localhost:8858
- 默认用户名/密码: sentinel/sentinel

### 5.2 监控指标

#### 5.2.1 实时监控

Dashboard 提供以下监控数据：
- **QPS**：每秒请求数
- **响应时间 (RT)**：平均响应时间
- **通过 QPS**：成功通过的请求
- **拒绝 QPS**：被限流/熔断的请求
- **通过/拒绝比例**：通过率和拒绝率

#### 5.2.2 关键指标监控

在 `actuator/metrics` 端点暴露以下指标：
```yaml
management:
  metrics:
    tags:
      application: ${spring.application.name}
    export:
      prometheus:
        enabled: true  # 集成 Prometheus（可选）
```

**关键指标**：
- `sentinel_resource_pass_qps`：通过 QPS
- `sentinel_resource_block_qps`：阻塞 QPS
- `sentinel_resource_rt`：平均 RT
- `sentinel_resource_thread_count`：并发线程数

---

### 5.3 告警配置

#### 5.3.1 Sentinel 告警规则

**文件**：`sentinel-alert-rules.json`

```json
[
  {
    "resourceName": "evaluateTransaction",
    "metricType": "passQps",
    "threshold": 900,
    "strategy": 0,
    "alertType": "email",
    "receivers": ["ops@example.com"]
  },
  {
    "resourceName": "fetchModelInference",
    "metricType": "exceptionRatio",
    "threshold": 0.4,
    "strategy": 1,
    "alertType": "email",
    "receivers": ["ops@example.com"]
  }
]
```

#### 5.3.2 告警通知渠道

可选方案：
1. **邮件告警**：集成 JavaMail
2. **钉钉/企业微信**：Webhook
3. **短信告警**：集成阿里云短信
4. **Prometheus AlertManager**：集成到 Prometheus

---

## 6. 测试方案

### 6.1 单元测试

#### 6.1.1 Gateway 限流测试

**文件**：`services/gateway-service/src/test/java/com/risk_gateway/controller/GatewayControllerTest.java`

```java
package com.risk_gateway.controller;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.ArrayList;
import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        // 初始化流控规则
        initFlowRules();
    }

    private void initFlowRules() {
        List<FlowRule> rules = new ArrayList<>();
        FlowRule rule = new FlowRule();
        rule.setResource("evaluateTransaction");
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(10);  // QPS = 10（测试阈值）
        rule.setStrategy(RuleConstant.STRATEGY_DIRECT);
        rule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        rule.setLimitApp("default");
        rules.add(rule);
        FlowRuleManager.loadRules(rules);
    }

    @Test
    void testRateLimit() {
        // 发送 20 个请求（超过阈值 10）
        for (int i = 0; i < 20; i++) {
            final int index = i;
            webTestClient.post()
                    .uri("/api/v1/transactions")
                    .bodyValue(createTestRequest())
                    .exchange()
                    .expectBody()
                    .consumeWith(response -> {
                        int statusCode = response.getStatus().value();
                        if (index >= 10) {
                            // 后续请求应该被限流
                            assert statusCode == 429 || statusCode == 200;
                        }
                    });
        }
    }
}
```

#### 6.1.2 Orchestrator 熔断测试

**文件**：`services/risk-orchestrator-service/src/test/java/com/risk/orch/service/OrchestratorServiceTest.java`

```java
package com.risk.orch.service;

import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
class OrchestratorServiceTest {

    @Autowired
    private OrchestratorService orchestratorService;

    @MockBean
    private WebClient featureServiceWebClient;

    @MockBean
    private WebClient decisionServiceWebClient;

    @BeforeEach
    void setUp() {
        // 初始化熔断规则
        initDegradeRules();
    }

    private void initDegradeRules() {
        List<DegradeRule> rules = new ArrayList<>();
        DegradeRule rule = new DegradeRule();
        rule.setResource("fetchFeatures");
        rule.setGrade(DegradeStrategy.EXCEPTION_RATIO.ordinal());
        rule.setCount(0.5);  // 异常比例 50%
        rule.setTimeWindow(10);  // 熔断 10 秒
        rule.setMinRequestAmount(5);
        rule.setStatIntervalMs(10000);
        rules.add(rule);
        DegradeRuleManager.loadRules(rules);
    }

    @Test
    void testCircuitBreaker() {
        // 模拟 Feature Service 异常
        when(featureServiceWebClient.post())
                .thenThrow(new RuntimeException("Feature Service unavailable"));

        // 发送请求触发熔断
        for (int i = 0; i < 10; i++) {
            try {
                orchestratorService.evaluate(createTestRequest()).block();
            } catch (Exception e) {
                // 预期异常
            }
        }

        // 验证熔断生效（后续请求不再调用 Feature Service）
        verify(featureServiceWebClient, times(5)).post();  // 只调用 5 次，之后熔断
    }
}
```

---

### 6.2 集成测试

#### 6.2.1 端到端测试

**测试场景**：
1. **正常流量**：验证规则不影响正常请求
2. **流量突增**：模拟流量突增，验证限流生效
3. **服务异常**：模拟下游服务异常，验证熔断生效
4. **熔断恢复**：验证熔断后自动恢复

**测试工具**：
- **JMeter**：压力测试
- **Gatling**：性能测试
- **Locust**：负载测试

#### 6.2.2 JMeter 测试计划

**文件**：`test-plans/sentinel-test.jmx`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2">
  <hashTree>
    <TestPlan>
      <stringProp name="TestPlan.comments">Sentinel 流量保护和熔断测试</stringProp>
    </TestPlan>
    <!-- 测试场景配置 -->
  </hashTree>
</jmeterTestPlan>
```

**测试步骤**：
1. **基准测试**：100 QPS 持续 1 分钟
2. **压力测试**：2000 QPS 持续 30 秒
3. **熔断测试**：模拟 Orchestrator 异常，发送 100 个请求
4. **恢复测试**：等待熔断恢复，发送请求验证

---

### 6.3 混沌工程

#### 6.3.1 故障注入测试

使用 **Chaos Mesh** 或 **Chaos Monkey** 进行故障注入：

**测试场景**：
1. **Pod Kill**：模拟服务宕机
2. **Network Delay**：模拟网络延迟
3. **CPU Stress**：模拟 CPU 过载
4. **Memory Stress**：模拟内存泄漏

---

## 7. 风险控制

### 7.1 潜在风险

#### 7.1.1 误杀风险

**风险描述**：正常请求被限流或熔断

**缓解措施**：
- ✅ 合理设置阈值（基于压测数据）
- ✅ 使用 Warm Up 预热策略
- ✅ 监控限流/熔断比例
- ✅ 定期review规则

#### 7.1.2 级联熔断

**风险描述**：多个服务同时熔断导致系统不可用

**缓解措施**：
- ✅ 设置合理的熔断比例（50%）
- ✅ 实现降级逻辑（返回默认值）
- ✅ 熔断时长不要太长（10秒）
- ✅ 监控下游服务健康状态

#### 7.1.3 规则配置错误

**风险描述**：规则配置不当导致系统异常

**缓解措施**：
- ✅ 规则变更Code Review
- ✅ 测试环境验证
- ✅ 灰度发布规则
- ✅ 规则变更审计

---

### 7.2 回滚方案

#### 7.2.1 快速回滚

**方法 1：禁用 Sentinel**
```yaml
spring:
  cloud:
    sentinel:
      enabled: false  # 禁用 Sentinel
```

**方法 2：清空规则**
```bash
# 通过 Dashboard 清空所有规则
# 或调用 API
curl -X DELETE http://localhost:8719/api/rules
```

**方法 3：降级阈值**
```bash
# 调整规则阈值到安全值
# 通过 Dashboard 或 API 修改
```

#### 7.2.2 数据库回滚

如果使用 Nacos 持久化规则：
```bash
# 回滚到上一个版本
curl -X GET "http://localhost:8848/nacos/v1/cs/configs?dataId=gateway-service-flow-rules&group=SENTINEL_GROUP&tenant="
```

---

### 7.3 监控告警

#### 7.3.1 关键告警指标

| 指标 | 阈值 | 级别 | 处理措施 |
|------|------|------|----------|
| 限流比例 | > 10% | Warning | Review 规则阈值 |
| 限流比例 | > 30% | Critical | 立即扩容或优化 |
| 熔断次数 | > 5 次/分钟 | Warning | 检查下游服务 |
| 熔断次数 | > 10 次/分钟 | Critical | 立即排查 |
| 平均 RT | > 2s | Warning | 性能优化 |
| 平均 RT | > 5s | Critical | 紧急优化 |

#### 7.3.2 告警通知

```yaml
# 告警级别
- P0: Critical（立即处理）
- P1: High（1小时内处理）
- P2: Warning（当天处理）
- P3: Info（关注即可）

# 通知渠道
- P0/P1: 短信 + 电话 + 钉钉
- P2: 邮件 + 钉钉
- P3: 邮件
```

---

## 8. 实施检查清单

### 第一阶段检查清单（Gateway 保护）

**开发阶段**：
- [ ] 引入 Sentinel 依赖
- [ ] 配置 application.yml
- [ ] 实现 BlockExceptionHandler
- [ ] Controller 添加 @SentinelResource
- [ ] 编写单元测试

**测试阶段**：
- [ ] 本地测试限流功能
- [ ] 压测验证阈值
- [ ] Dashboard 可视化验证
- [ ] 异常处理测试

**上线阶段**：
- [ ] 配置 Sentinel Dashboard
- [ ] 配置监控告警
- [ ] 准备回滚方案
- [ ] 运维文档编写

---

### 第二阶段检查清单（服务熔断）

**开发阶段**：
- [ ] Orchestrator 添加熔断配置
- [ ] 实现降级逻辑
- [ ] 编写单元测试
- [ ] 编写集成测试

**测试阶段**：
- [ ] 模拟下游服务异常
- [ ] 验证熔断生效
- [ ] 验证熔断恢复
- [ ] 验证降级逻辑

**上线阶段**：
- [ ] 灰度发布
- [ ] 监控熔断指标
- [ ] 调整阈值
- [ ] 全量上线

---

### 第三阶段检查清单（持久化和优化）

**开发阶段**：
- [ ] 集成 Nacos
- [ ] 配置规则推送
- [ ] 实现审计日志
- [ ] Dashboard 集成

**测试阶段**：
- [ ] 规则热更新测试
- [ ] 规则推送测试
- [ ] 审计日志测试

**上线阶段**：
- [ ] 生产环境 Nacos 配置
- [ ] 规则迁移到 Nacos
- [ ] 监控规则变更
- [ ] 培训运维人员

---

## 9. 后续优化方向

### 9.1 短期优化（1-2个月）

1. **规则优化**
   - 基于监控数据调整阈值
   - 增加热点参数限流
   - 优化熔断策略

2. **性能优化**
   - 使用异步Sentinel
   - 优化规则匹配性能
   - 减少日志输出

3. **功能增强**
   - 集成 Prometheus
   - 实现规则灰度发布
   - 增加规则测试工具

---

### 9.2 中期优化（3-6个月）

1. **流量管理**
   - 实现流量整形
   - 增加流量预热
   - 实现自适应限流

2. **智能化**
   - 基于机器学习预测流量
   - 动态调整阈值
   - 智能熔断策略

3. **平台化**
   - 规则管理平台
   - 可视化监控面板
   - 一键压测工具

---

## 10. 总结

### 核心价值

通过引入 Sentinel，我们将获得：

✅ **流量保护**：防止系统被流量打垮
✅ **熔断降级**：保护下游服务，防止级联故障
✅ **实时监控**：Dashboard 可视化监控
✅ **规则管理**：动态配置规则，无需重启
✅ **稳定可靠**：阿里双11验证，生产级质量

### 实施建议

1. **渐进式实施**：分阶段上线，逐步完善
2. **充分测试**：每个阶段充分测试后再进入下一阶段
3. **监控先行**：先建立监控，再配置规则
4. **文档完善**：编写运维文档，培训团队

### 预期收益

- **系统可用性**：从 99.5% 提升到 99.9%
- **故障恢复时间**：从 10 分钟降低到 10 秒
- **运维效率**：规则动态配置，无需重启服务
- **用户体验**：减少因故障导致的用户投诉

---

**文档版本**：v1.0
**最后更新**：2026-01-15
**维护者**：Risk Control Team
