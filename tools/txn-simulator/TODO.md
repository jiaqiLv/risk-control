# TXN-SIMULATOR 功能扩充计划

> 最后更新：2026-01-15
>
> 本文档记录 txn-simulator 模块的当前功能和未来扩充计划

---

## 📊 目录

- [已实现功能](#已实现功能)
- [扩充计划（按优先级）](#扩充计划按优先级)
- [技术栈推荐](#技术栈推荐)
- [实施路线图](#实施路线图)

---

## ✅ 已实现功能

### 核心功能

#### 1. 数据处理能力
- [x] IEEE-CIS 数据集解析（支持 400+ 字段）
- [x] Transaction + Identity 数据关联（Left Join）
- [x] 灵活采样（data-sample-rate 配置）
- [x] 场景过滤（欺诈、合法、冷启动、高缺失率）
- [x] ProductCD 过滤
- [x] 分页支持（startIndex, maxRecords）

#### 2. 五种回放模式
- [x] **COMMON** - 简单顺序处理
- [x] **FIXED_QPS** - 固定 QPS 负载测试（Guava RateLimiter）
- [x] **REPLAY_DT** - 基于时间的真实流量回放（支持加速倍率）
- [x] **SCENARIO** - 场景化快速测试
- [x] **STREAMING** - 响应式流式处理（Project Reactor）

#### 3. 多协议支持
- [x] HTTP/REST（Spring WebFlux WebClient）
- [x] gRPC（Python 推理服务集成）
- [x] Kafka 消息队列（JSON 序列化）

#### 4. 高级特性
- [x] 冷启动模拟（生成 synthetic user IDs）
- [x] 并发控制（Semaphore, Max In-Flight）
- [x] 速率限制（Guava RateLimiter）
- [x] 连接池优化（500 max connections）
- [x] 优雅关闭（Graceful Shutdown）

#### 5. 评估与监控
- [x] 延迟统计（Min, Max, Avg, P50/P75/P90/P95/P99）
- [x] 直方图分布（1ms - 5000ms buckets）
- [x] 混淆矩阵（TP, TN, FP, FN）
- [x] 模型评估指标（Accuracy, Precision, Recall, F1, AUC, KS）
- [x] 多格式输出（CSV, JSONL, JSON）

#### 6. 架构设计
- [x] 分层架构（source → mapping → client → runner → metrics → eval）
- [x] 依赖注入（Spring IoC）
- [x] 策略模式（多 replay modes）
- [x] 建造者模式（SimulationResponse）
- [x] 响应式编程（Project Reactor）

---

## 🚀 扩充计划（按优先级）

### 🔴 高优先级（立即实施）

#### 1. 分布式追踪（OpenTelemetry）
**目标：** 端到端全链路追踪，快速定位性能瓶颈

**实施内容：**
- 集成 OpenTelemetry SDK
- 为所有 client 调用添加 span
- 集成 Jaeger/Zipkin 追踪后端
- Trace ID 与日志关联

**预期收益：**
- 可视化完整调用链路（txn-simulator → Gateway → Risk Engine → Model Service）
- 快速定位慢请求根因（精确到具体服务/数据库查询）
- 性能瓶颈分析（哪个服务耗时最长）

**工作量：** 2-3 天

**技术栈：**
- `io.opentelemetry:opentelemetry-api`
- `io.opentelemetry:opentelemetry-spring-boot-starter`
- Jaeger / Zipkin

---

#### 2. Grafana 实时监控大盘
**目标：** 可视化监控指标，实时洞察系统状态

**实施内容：**
- 集成 Micrometer + Prometheus
- 配置 Prometheus 拉取 metrics
- 创建 Grafana Dashboard：
  - 实时 QPS（近 1 分钟）
  - 延迟曲线（P50/P95/P99）
  - 错误率趋势
  - 模型性能指标（召回率、准确率）
- 配置告警规则（钉钉/Slack/Email）

**预期收益：**
- 实时监控（无需查看日志文件）
- 告警自动化（异常自动通知）
- 趋势分析（性能变化一目了然）
- 历史对比（多次运行对比）

**工作量：** 3-5 天

**技术栈：**
- Micrometer Prometheus
- Prometheus Server
- Grafana
- AlertManager

---

#### 3. 异步性能优化
**目标：** 全链路异步，提升吞吐量 50%+

**实施内容：**
- **阶段 1：** CSV 流式读取
  - 使用 `BufferedReader` + `Flux.generate` 流式解析
  - 避免一次性加载全部数据到内存
- **阶段 2：** 异步批量写入
  - `ResultSink` 使用 `AsyncOutputStream` 或 `WriteThreadPool`
  - 批量刷新（每 1000 条异步写入）
- **阶段 3：** 并行指标计算
  - `OfflineEvaluator` 使用 `ForkJoinPool` 并行计算 AUC/KS
  - 减少评估阶段耗时

**预期收益：**
- 吞吐量提升 50-100%
- 内存占用减少 40-60%
- 支持更大规模数据（千万级记录）

**工作量：** 1-2 周

**技术栈：**
- Project Reactor（已使用）
- Java ForkJoinPool
- NIO Async Channel

---

#### 4. 自动化回归测试（CI/CD 集成）
**目标：** 每次代码提交自动运行压测，防止性能退化

**实施内容：**
- **GitHub Actions / GitLab CI Pipeline：**
  ```yaml
  trigger: push / pull_request
  steps:
    1. 编译构建
    2. 运行小型压测（1000 条记录，基准数据集）
    3. 检查指标：
       - P95 延迟未恶化（< 5%）
       - 召回率未下降（< 2%）
       - 无新增错误
    4. 测试通过 → 允许合并
    5. 测试失败 → 阻止合并 + 发送通知
  ```
- **基准数据集：**
  - 创建固定的 `regression-test.csv`（1000 条代表性交易）
  - 建立基准指标文件（`baseline-metrics.json`）
- **契约测试：**
  - 使用 Pact 验证 API 契约
  - 防止破坏性 API 变更

**预期收益：**
- 自动化质量保障（无需手动测试）
- 早期发现问题（合并前而非上线后）
- 防止性能退化（自动对比基准）

**工作量：** 1 周

**技术栈：**
- GitHub Actions / GitLab CI
- JUnit 5
- TestContainers
- Pact（可选）

---

### 🟡 中优先级（3-6 个月）

#### 5. 实时流处理（Apache Flink）
**目标：** 支持实时监控和在线 A/B 测试

**实施内容：**
- **流式架构：**
  ```
  生产 Kafka → Flink → txn-simulator (实时模式)
                     ↓
                  影子测试（同时发送到新旧模型）
                     ↓
                  实时对比（Grafana Dashboard）
  ```
- **功能特性：**
  - 实时监控生产交易（无需导出 CSV）
  - 在线 A/B 测试（50% 流量到 Model A，50% 到 Model B）
  - 影子测试（生产流量同时发送到新旧模型，但只用旧模型结果）
  - 窗口统计（滑动窗口 1min/5min/1hour）
  - 动态流量注入（无需重启）

**预期收益：**
- 真实生产环境监控（实时捕获问题）
- 模型对比更准确（真实流量而非历史数据）
- 快速验证（无需等待离线处理）

**工作量：** 2-3 周

**技术栈：**
- Apache Flink
- Kafka Streams（轻量级替代方案）
- Pulsar（替代 Kafka）

---

#### 6. 多数据源支持
**目标：** 不再仅依赖 CSV，支持多种数据源

**实施内容：**
- **数据源抽象层：**
  ```java
  interface DataSource {
      Flux<TransactionRecord> readRecords();
  }

  implementations:
      - CsvDataSource (当前)
      - JdbcDataSource (MySQL/PostgreSQL)
      - MongoDataSource (MongoDB)
      - KafkaDataSource (Kafka Topic)
      - S3DataSource (AWS S3 Parquet files)
  ```
- **配置示例：**
  ```yaml
  data-source:
    type: JDBC  # CSV, JDBC, MONGO, KAFKA, S3
    url: "jdbc:mysql://prod-db:3306/transactions"
    query: "SELECT * FROM transactions WHERE event_date = CURDATE() LIMIT 10000"
  ```

**预期收益：**
- 直接从生产数据库采样（无需导出 CSV）
- 跨数据源联合测试（交易库 + 用户画像库）
- 支持大数据平台（Hive, Spark, Snowflake）

**工作量：** 2-3 周

**技术栈：**
- Spring Data JDBC
- Spring Data MongoDB
- Apache Parquet
- AWS S3 SDK

---

#### 7. Kubernetes 云原生部署
**目标：** 弹性伸缩，支持大规模分布式压测

**实施内容：**
- **容器化：**
  - 编写 Dockerfile（多阶段构建）
  - 推送到私有 Registry（Harbor/ECR）
- **Kubernetes 部署：**
  ```yaml
  apiVersion: apps/v1
  kind: Deployment
  metadata:
    name: txn-simulator-worker
  spec:
    replicas: 10  # 10 个 worker 并发压测
    template:
      spec:
        containers:
          - name: simulator
            image: txn-simulator:latest
            resources:
              limits:
                cpu: "2"
                memory: "4Gi"
  ```
- **HPA 自动扩缩容：**
  ```yaml
  apiVersion: autoscaling/v2
  kind: HorizontalPodAutoscaler
  spec:
    minReplicas: 5
    maxReplicas: 50
    metrics:
      - type: Resource
        resource:
          name: cpu
          target:
            type: Utilization
            averageUtilization: 70
  ```
- **Helm Chart：** 一键部署

**预期收益：**
- 横向扩展（10x-100x QPS 提升）
- 弹性伸缩（CPU 高时自动扩容）
- 自愈能力（Pod 崩溃自动重启）
- 资源隔离（namespace/资源配额）

**工作量：** 1-2 周

**技术栈：**
- Kubernetes
- Docker
- Helm
- Istio（可选，服务网格）

---

#### 8. 智能分析报告
**目标：** 自动生成洞察报告，检测数据漂移和模型退化

**实施内容：**
- **数据漂移检测：**
  - 计算 PSI（Population Stability Index）
  - 特征分布变化对比（训练数据 vs 当前数据）
  - 预测概率漂移监控
- **模型退化分析：**
  - 按时间窗口分解性能（每日/每周对比）
  - 识别退化最快的产品线或地区
  - 根因分析（是新欺诈类型？还是特征失效？）
- **可解释性：**
  - SHAP 值分析（哪些特征最重要）
  - 误案例分析（为什么这笔交易误判？）
- **业务影响评估：**
  - 拦截金额（误拦截带来的业务损失）
  - 漏损金额（未拦截的欺诈金额）
  - ROI 分析（模型改进带来的收益）

**预期收益：**
- 自动化模型监控（无需人工分析）
- 早期发现退化（性能下降时立即告警）
- 业务视角（从技术指标转换为业务价值）

**工作量：** 2-3 周

**技术栈：**
- Great Expectations（数据质量）
- WhyLabs（模型监控）
- Evidently AI（ML 监控）
- Jupyter（报告生成）

---

### 🟢 低优先级（长期规划）

#### 9. Web UI 可视化控制台
**目标：** 降低使用门槛，提供友好的 Web 界面

**实施内容：**
- **前端框架：** Vue.js 3 + Element Plus
- **后端 API：** Spring Boot + REST
- **功能模块：**
  1. 配置管理（表单化配置，无需手写 YAML）
  2. 实时监控（进度条、曲线图、日志流）
  3. 报告可视化（交互式图表、对比视图）
  4. 一键操作（开始/停止/下载报告）
  5. 场景模板库（冷启动测试、欺诈回放等）

**预期收益：**
- 降低使用门槛（非技术人员也能使用）
- 提升效率（可视化配置 vs YAML 编写）
- 团队协作（共享测试场景）

**工作量：** 3-4 周

**技术栈：**
- Vue.js 3 / React
- Element Plus / Ant Design
- Spring Boot Web
- WebSocket（实时推送）

---

#### 10. AI 驱动的场景生成
**目标：** 使用 GAN 生成新型欺诈攻击场景

**实施内容：**
- **GAN 训练：**
  - 输入：历史欺诈交易特征
  - 输出：新型潜在攻击场景
  - 验证：人工审核 + 模型预测
- **合成数据工具：**
  - SDV (Synthetic Data Vault)
  - DataSynthesizer（隐私保护）
- **应用场景：**
  - 生成罕见但高风险的交易
  - 覆盖历史数据未包含的案例
  - 生成脱敏数据用于团队测试

**预期收益：**
- 发现新型攻击模式（提前防御）
- 长尾场景覆盖（提升模型鲁棒性）
- 隐私保护（无需暴露真实用户数据）

**工作量：** 1-2 个月

**技术栈：**
- TensorFlow / PyTorch
- GAN (Generative Adversarial Networks)
- SDV (Synthetic Data Vault)
- Python ML 生态

---

#### 11. 混沌工程集成
**目标：** 故障场景压测，验证系统韧性

**实施内容：**
- **故障注入：**
  - 网络故障：延迟、丢包、分区
  - 服务故障：随机杀死 Pod、资源耗尽
  - 依赖故障：数据库超时、Redis 不可用
- **验证指标：**
  - 系统可用性（是否仍然 ≥ 99.9%）
  - 降级策略是否正确触发
  - 恢复时间（MTTR）
- **工具集成：**
  - Chaos Mesh（Kubernetes）
  - Chaos Monkey（Netflix）

**预期收益：**
- 提升系统韧性（提前发现单点故障）
- 验证应急预案（故障时能否快速恢复）
- 混沌演练（每月故障模拟）

**工作量：** 2-3 周

**技术栈：**
- Chaos Mesh
- Chaos Monkey
- Gremlin（商业方案）

---

#### 12. 安全性增强
**目标：** 生产级数据脱敏和访问控制

**实施内容：**
- **敏感数据脱敏：**
  - 卡号：`****-****-****-1234`
  - 手机号：`138****5678`
  - 邮箱：`j***@gmail.com`
- **访问控制（RBAC）：**
  - 角色：Viewer（只读）、Tester（运行测试）、Admin（全部权限）
  - SSO 集成（LDAP/SAML/OAuth 2.0）
  - 审计日志（谁、何时、做了什么）
- **合规性：**
  - 符合 GDPR/CCPA/个人信息保护法
  - 安全审计支持

**预期收益：**
- 数据安全（防止敏感信息泄露）
- 合规性（通过安全审计）
- 权限管控（防止误操作）

**工作量：** 1-2 周

**技术栈：**
- Spring Security
- OAuth 2.0 / OIDC
- Keycloak / Okta
- Apache ShardingSphere（脱敏）

---

#### 13. DSL 脚本支持
**目标：** 声明式配置，无需重新编译

**实施内容：**
- **Kotlin DSL 示例：**
  ```kotlin
  scenario "Cold Start Test" {
      dataSource = "production_db"
      sampleRate = 0.01
      coldStartRatio = 0.2

      mode = REPLAY_DT {
          speedMultiplier = 60.0
          preserveOrder = true
      }

      target {
          url = "http://risk-engine:8080"
          qps = 100
          concurrency = 20
      }

      assertions {
          p95Latency < 200ms
          recallRate > 0.90
          errorRate < 0.5%
      }
  }
  ```

**预期收益：**
- 无需重新编译（修改脚本立即生效）
- 团队共享测试场景库
- 版本控制（Git 管理 DSL 脚本）

**工作量：** 2-3 周

**技术栈：**
- Kotlin DSL
- Groovy DSL
- JSON Schema（配置验证）

---

## 🛠️ 技术栈推荐总结

### 性能优化
| 技术 | 用途 | 优先级 |
|------|------|--------|
| Project Reactor | 响应式编程（已使用） | ✅ 已集成 |
| Vert.x | 高性能异步工具包 | 🟡 可选 |
| Apache Arrow | 零拷贝列式内存格式 | 🟢 长期 |
| Chronicle Map | 超大内存映射 Map | 🟢 长期 |

### 可观测性
| 技术 | 用途 | 优先级 |
|------|------|--------|
| OpenTelemetry | 分布式追踪标准 | 🔴 高 |
| Jaeger / Zipkin | 追踪后端 | 🔴 高 |
| Prometheus + Grafana | 监控大盘 | 🔴 高 |
| Micrometer | Metrics 抽象 | 🔴 高 |
| Great Expectations | 数据质量监控 | 🟡 中 |
| Evidently AI | ML 模型监控 | 🟡 中 |

### 流处理
| 技术 | 用途 | 优先级 |
|------|------|--------|
| Apache Flink | 流处理引擎 | 🟡 中 |
| Kafka Streams | 轻量级流处理 | 🟡 中 |
| Apache Spark | 大数据处理 | 🟡 中 |

### 数据源
| 技术 | 用途 | 优先级 |
|------|------|--------|
| Spring Data JDBC | 关系数据库 | 🟡 中 |
| Spring Data MongoDB | NoSQL | 🟡 中 |
| Apache Parquet | 列式存储 | 🟡 中 |
| AWS S3 SDK | 对象存储 | 🟡 中 |

### 压测工具
| 技术 | 用途 | 优先级 |
|------|------|--------|
| Gatling | 分布式压测 | 🟢 长期 |
| Locust | Python 压测 | 🟢 长期 |
| K6 | 现代化压测 | 🟢 长期 |

### 容器与编排
| 技术 | 用途 | 优先级 |
|------|------|--------|
| Docker | 容器化 | 🟡 中 |
| Kubernetes | 容器编排 | 🟡 中 |
| Helm | K8s 包管理 | 🟡 中 |
| Istio | 服务网格 | 🟢 长期 |

### 测试
| 技术 | 用途 | 优先级 |
|------|------|--------|
| JUnit 5 | 单元测试 | 🔴 高 |
| TestContainers | 集成测试 | 🔴 高 |
| Pact | 契约测试 | 🟡 中 |

### 前端
| 技术 | 用途 | 优先级 |
|------|------|--------|
| Vue.js 3 | 前端框架 | 🟢 长期 |
| Element Plus | UI 组件库 | 🟢 长期 |
| React | 替代方案 | 🟢 长期 |
| Ant Design | UI 组件库 | 🟢 长期 |

### 安全
| 技术 | 用途 | 优先级 |
|------|------|--------|
| Spring Security | 认证授权 | 🟢 长期 |
| OAuth 2.0 / OIDC | 认证协议 | 🟢 长期 |
| Keycloak | 身份管理 | 🟢 长期 |
| Apache ShardingSphere | 数据脱敏 | 🟢 长期 |

---

## 📅 实施路线图

### Phase 1：可观测性提升（1 个月）
- ✅ Week 1-2: OpenTelemetry 集成 + Jaeger 部署
- ✅ Week 3-4: Prometheus + Grafana 监控大盘

**目标：** 实时洞察系统状态，快速定位性能瓶颈

---

### Phase 2：性能优化与自动化（2 个月）
- ✅ Month 2 Week 1-2: 异步性能优化（流式读取、异步写入）
- ✅ Month 2 Week 3-4: CI/CD 自动化回归测试
- ✅ Month 3 Week 1-2: 基准数据集建立

**目标：** 吞吐量提升 50%，自动防止性能退化

---

### Phase 3：实时能力与云原生（3 个月）
- ✅ Month 4 Week 1-3: Apache Flink 实时流处理
- ✅ Month 5 Week 1-3: 多数据源支持（JDBC, MongoDB, Kafka）
- ✅ Month 6 Week 1-2: Kubernetes 容器化部署
- ✅ Month 6 Week 3-4: Helm Chart + HPA 自动扩缩容

**目标：** 支持实时监控和大规模分布式压测

---

### Phase 4：智能化与易用性（4 个月）
- ✅ Month 7-8: 智能分析报告（数据漂移、模型退化）
- ✅ Month 9-10: Web UI 可视化控制台

**目标：** 降低使用门槛，自动化模型监控

---

### Phase 5：高级特性（按需实施）
- ⏸️ AI 场景生成（GAN）
- ⏸️ 混沌工程集成
- ⏸️ 安全性增强（脱敏、RBAC）
- ⏸️ DSL 脚本支持

**目标：** 探索性功能，按业务需求实施

---

## 📝 实施建议

### 开发原则
1. **渐进式增强** - 每个阶段独立交付，逐步迭代
2. **向后兼容** - 新功能不破坏现有配置和接口
3. **配置驱动** - 通过配置开关功能，无需重新编译
4. **测试优先** - 每个新功能都有自动化测试覆盖

### 团队协作
1. **Code Review** - 所有 PR 必须经过 review
2. **文档同步** - 代码变更同步更新 README 和配置示例
3. **知识共享** - 每个功能实施后团队分享会

### 风险控制
1. **灰度发布** - 新功能先在测试环境验证，再上生产
2. **回滚预案** - 每次部署前准备回滚方案
3. **监控告警** - 新功能上线前配置好监控和告警

---

## 📚 参考资源

### 文档
- [Spring Boot 官方文档](https://spring.io/projects/spring-boot)
- [Project Reactor 参考指南](https://projectreactor.io/docs)
- [OpenTelemetry 文档](https://opentelemetry.io/docs/)
- [Apache Flink 文档](https://flink.apache.org/docs/)

### 最佳实践
- [微服务测试最佳实践](https://martinfowler.com/articles/microservice-testing/)
- [混沌工程实践](https://principlesofchaos.org/)
- [持续集成最佳实践](https://www.atlassian.com/continuous-delivery/principles/continuous-integration-vs-continuous-delivery)

### 工具
- [Grafana Dashboard 模板](https://grafana.com/grafana/dashboards/)
- [Kubernetes 官方文档](https://kubernetes.io/docs/)
- [Prometheus 最佳实践](https://prometheus.io/docs/practices/)

---

## 🤝 贡献指南

欢迎团队成员提交扩充建议！

1. 编辑本文档，添加新的 TODO 项
2. 标注优先级（🔴 高 / 🟡 中 / 🟢 低）
3. 估算工作量和预期收益
4. 提交 PR 并团队评审

---

**维护者：** Risk Control Team
**最后更新：** 2026-01-15
