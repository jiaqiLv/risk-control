# Java 风控系统参考项目

> 本文档整理了与本项目契合度较高的 Java 风控相关开源项目，为系统架构优化和技术选型提供参考。

**当前项目概况**：
- **技术栈**：Spring Boot 3.2.2 + Java 17
- **架构**：微服务架构（Gateway、Orchestrator、Feature、Decision 等服务）
- **核心特性**：实时交易风控、规则引擎 + 模型推理混合模式、gRPC 通信
- **目标场景**：交易反欺诈、实时风控决策

---

## 🎯 最契合项目

### 1. Radar - 实时风控引擎 ⭐⭐⭐⭐⭐

**项目链接**：
- GitHub: https://github.com/wfh45678/radar
- Gitee: https://gitee.com/freshday/radar
- 官网: https://www.riskengine.cn
- Wiki: https://gitee.com/freshday/radar/wikis/home

**为什么契合度最高**：
- ✅ **技术栈匹配**：Spring Boot + Groovy + MongoDB + Redis + Elasticsearch
- ✅ **场景一致**：专为反欺诈和交易风控设计
- ✅ **响应快速**：特殊场景可做到 100ms 内响应
- ✅ **可视化规则编辑器**：支持中文，易用性强
- ✅ **架构相似**：前后端分离，模块化设计
- ✅ **开箱即用**：配置简单，真正的开箱即用

**技术栈详解**：
- **后端框架**：Spring Boot 2.2.5 + Mybatis + tkMapper + MySQL
- **规则引擎**：Groovy（动态生成规则，实时编辑，即时生效）
- **数据存储**：
  - MySQL：风险模型元信息
  - MongoDB：事件 JSON 存储，提供统计计算（max、min、sum、avg）
  - Redis：缓存 + 发布订阅监听配置更新
  - Elasticsearch：数据查询和规则命中报表
- **前端框架**：React（SPA）
- **API 文档**：Swagger

**项目模块**：
```
radar/
├── radar-admin/           # 管理端（规则配置、监控）
├── radar-engine/          # 核心风控引擎
├── radar-service/         # 服务层
├── radar-service-impl/    # 服务实现
├── radar-dao/             # 数据访问层
├── radar-dal/             # 数据访问层实现
├── radar-commons/         # 公共模块
├── radar-kafka-demo/      # Kafka 集成示例
└── resources/             # 资源文件（SQL、图片、配置）
```

**核心特性**：
1. **实时风控**：特殊场景可做到 100ms 内响应
2. **可视化规则编辑器**：丰富的运算符、计算规则灵活
3. **自定义规则引擎**：支持复杂多变的场景
4. **插件化设计**：快速接入其它数据能力平台
5. **NoSQL 架构**：易扩展，高性能
6. **支持中文**：易用性更强

**在线演示**：
- Demo URL: https://www.riskengine.cn
- 建议自行注册用户，避免使用测试账号受干扰

**学习价值**：
- ✅ 完整的风控系统架构设计
- ✅ 可视化规则配置界面实现
- ✅ Groovy 动态规则引擎实战
- ✅ 规则管理和监控体系
- ✅ 多数据库集成实践

**与本项目结合点**：
- 参考 Groovy 动态规则引擎机制，增强 `decision-service` 的规则管理能力
- 学习可视化规则编辑器设计，构建规则配置平台
- 借鉴插件化设计思想，提升系统扩展性
- 参考 MongoDB + ES 的数据存储方案，优化性能

---

### 2. Drools 规则引擎 ⭐⭐⭐⭐⭐

**项目链接**：
- 官网: https://drools.org/
- GitHub: https://github.com/kiegroup/drools
- 文档: https://docs.drools.org/

**为什么推荐**：
- ✅ **业界标准**：最成熟的 Java 规则引擎，大量企业级应用
- ✅ **功能强大**：支持复杂逻辑、决策表、规则流、决策树
- ✅ **社区活跃**：KIE 社区维护，文档完善，案例丰富
- ✅ **Spring Boot 集成**：有成熟的集成方案（`drools-spring-boot-starter`）
- ✅ **RETE 算法**：高效的规则匹配算法
- ✅ **决策表支持**：Excel 配置规则，业务人员可维护

**核心功能**：
1. **规则引擎**：基于 RETE 算法的高效规则匹配
2. **决策表**：Excel 格式的规则配置
3. **规则流**：可视化的规则执行流程编排
4. **决策树**：树形决策模型
5. **复杂事件处理（CEP）**：实时事件流处理
6. **DRL 语言**：声明式规则定义语言

**技术特点**：
- **纯 Java 实现**：无额外语言依赖
- **低延迟**：RETE 网络优化，规则执行效率高
- **可扩展**：支持自定义函数、Operators
- **云原生**：支持 Kubernetes、Docker 部署

**Spring Boot 集成示例**：
```xml
<dependency>
    <groupId>org.drools</groupId>
    <artifactId>drools-core</artifactId>
    <version>8.44.0.Final</version>
</dependency>
<dependency>
    <groupId>org.drools</groupId>
    <artifactId>drools-decisiontables</artifactId>
    <version>8.44.0.Final</version>
</dependency>
```

**与本项目结合点**：
- **替换或增强** `decision-service` 的规则引擎
- **决策表配置**：让风控策略人员通过 Excel 维护规则
- **规则流编排**：实现复杂的规则执行顺序
- **CEP 能力**：增强实时事件处理能力

**适用场景**：
- 复杂业务规则管理
- 需要业务人员参与规则配置
- 高性能规则匹配场景
- 决策表、决策树等结构化规则

**学习资源**：
- [Spring Boot with Drools Rules Engine](https://medium.com/@tobintom/spring-boot-with-drools-rules-engine-d73e1af3c411)
- [决策引擎的内核及基于Drools开源引擎讲解](https://blog.csdn.net/weixin_45545159/article/details/117968805)

---

### 3. Easy Rules ⭐⭐⭐⭐

**项目链接**：
- GitHub: https://github.com/dvgaba/easy-rules
- 官网: https://github.com/j-easy/easy-rules/wiki

**为什么推荐**：
- ✅ **轻量级**：POJO + Java 8 Stream API，依赖少
- ✅ **简单易用**：学习成本低，上手快
- ✅ **注解驱动**：`@Rule`、`@Condition`、`@Action` 注解开发
- ✅ **灵活表达**：支持 MVEL/SpEL 表达式
- ✅ **适合简单规则**：参数校验、简单风控规则

**核心特性**：
1. **轻量级 API**：简单易用的编程模型
2. **POJO 开发**：无需继承特定类
3. **注解定义规则**：
   ```java
   @Rule(name = "年龄规则", priority = 1)
   public class AgeRule {
       @Condition
       public boolean when(@Fact("person") Person person) {
           return person.getAge() > 18;
       }
       @Action
       public void then(@Fact("person") Person person) {
           System.out.println("成年人");
       }
   }
   ```
4. **组合规则**：支持 Composite Pattern（UnitRuleGroup、ActivationRuleGroup）
5. **规则监听器**：支持规则执行前后回调
6. **动态规则加载**：支持 YAML、JSON 定义规则

**技术特点**：
- **基于 Java 8 Stream API**：现代化的编程模型
- **无外部依赖**：核心库零依赖
- **表达式支持**：MVEL、SpEL
- **规则描述符**：YAML、JSON 定义规则

**适用场景**：
- 参数校验规则
- 简单风控规则
- 审批流程引擎
- 数据验证

**与本项目结合点**：
- `gateway-service` 的请求参数校验
- `feature-service` 的特征提取规则
- 作为轻量级规则引擎补充复杂规则引擎

**学习资源**：
- [Easy Rules 规则引擎实战](https://blog.csdn.net/tmax52HZ/article/details/135325491)
- [java轻量级规则引擎easy-rules使用介绍](https://www.cnblogs.com/zt007/p/15745365.html)

---

## 🛡️ 辅助工具

### 4. Sentinel - 流量防卫兵 ⭐⭐⭐⭐⭐

**项目链接**：
- GitHub: https://github.com/alibaba/Sentinel
- 官网: https://sentinelguard.io/
- 中文文档: https://github.com/alibaba/Sentinel/wiki/%E4%BB%8B%E7%BB%8D

**为什么强烈推荐**：
- ✅ **阿里开源**：经过双11大流量验证
- ✅ **限流熔断**：保护网关和核心服务
- ✅ **实时监控**：提供监控面板（Sentinel Dashboard）
- ✅ **Spring Cloud 集成**：完美集成 Spring Boot / Spring Cloud
- ✅ **多种流控模式**：QPS、线程数、响应时间等
- ✅ **规则持久化**：支持 Nacos、Apollo、Zookeeper

**核心功能**：
1. **流量控制（Flow Control）**：
   - QPS（每秒查询数）限流
   - 线程数限流
   - 基于调用关系的流控
   - 匀速排队、冷启动、预热模式

2. **熔断降级（Circuit Breaking）**：
   - 异常比例熔断
   - 异常数熔断
   - 慢调用比例熔断
   - 自动恢复

3. **系统负载保护**：
   - CPU 使用率
   - 平均 RT
   - 并发线程数
   - 入口 QPS

4. **热点参数限流**：
   - 对热点数据进行精确限流
   - 支持参数级流控

5. **实时监控**：
   - 实时监控面板
   - 规则动态配置
   - 监控数据持久化

**Spring Boot 集成示例**：
```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-sentinel</artifactId>
</dependency>
```

**配置示例**：
```yaml
spring:
  cloud:
    sentinel:
      transport:
        dashboard: localhost:8080
      eager: true
      datasource:
        flow:
          nacos:
            server-addr: localhost:8848
            dataId: ${spring.application.name}-flow-rules
            rule-type: flow
```

**与本项目结合点**：
- ✅ **保护 `gateway-service`**：防止流量过载
- ✅ **保护 `orchestrator-service`**：下游服务故障时快速熔断
- ✅ **与现有降级策略契合**：完美契合项目的"智能降级策略"理念
- ✅ **监控面板**：可视化监控服务调用情况

**使用建议**：
1. **优先在 Gateway 集成**：作为第一道防线
2. **Orchestrator 熔断**：当 Python 模型服务不可用时，快速切换到规则引擎
3. **规则持久化**：使用 Nacos 管理流控规则

**学习资源**：
- [SpringCloud Alibaba之Sentinel实现熔断与限流](https://blog.csdn.net/qq_36813853/article/details/142318955)
- [阿里开源的限流器Sentinel，轻松实现接口限流！](https://developer.aliyun.com/article/841048)

---

### 5. sunpeak/riskcontrol - 轻量级实时业务风控系统 ⭐⭐⭐

**项目链接**：
- GitHub: https://github.com/sunpeak/riskcontrol
- Gitee: https://gitee.com/qchen007/riskcontrol

**核心特性**：
- ✅ **轻量级**：简单易用，上手快
- ✅ **实时业务风控**：分析风险事件
- ✅ **场景化规则**：根据场景动态调整规则
- ✅ **自动精准预警**：风险自动预警

**适用场景**：
- 快速搭建风控系统原型
- 学习风控系统基础架构
- 中小型项目风控需求

---

## 📚 学习资源项目

### 6. risk-talk - 风控架构方案 ⭐⭐⭐⭐

**项目链接**：
- GitHub: https://github.com/aalansehaiyang/risk-talk

**项目内容**：
- 风控架构方案
- Groovy 规则引擎实践
- URULE 规则引擎（基于 RETE 算法）
- 规则集、决策表、决策树、评分卡

**学习价值**：
- ✅ 多种规则引擎对比
- ✅ 架构设计参考
- ✅ 实战案例

---

## 🏆 大厂实践参考

### 7. 美团 Zeus 规则引擎

**文章链接**：
- [复杂风控场景下，如何打造一款高效的规则引擎](https://tech.meituan.com/2020/05/14/meituan-security-zeus.html)

**核心亮点**：
- ✅ **高效事件计数服务**：支持高并发场景
- ✅ **累积周期配置**：灵活的时间窗口配置
- ✅ **各种计算逻辑**：计数、去重、排序等
- ✅ **复杂风控场景实践**：生产级经验

**与本项目契合点**：
- 高性能特征计数
- 复杂规则计算逻辑
- 生产环境优化经验

---

### 8. 有赞风控规则引擎实践

**文章链接**：
- [有赞风控规则引擎实践](https://www.cnblogs.com/jpfss/p/10869920.html)

**核心亮点**：
- ✅ **实时（事中）引擎**：使用 `youzan-boot` 框架
- ✅ **事后引擎**：使用 Storm 实时流处理
- ✅ **生产级实践**：真实业务场景

**架构特点**：
- 事中：实时拦截交易请求
- 事后：批量分析，补充规则
- 规则热更新：无需重启服务

**学习价值**：
- 事中事后分离架构
- 流式计算在风控中的应用
- 规则热更新机制

---

### 9. 智能风控决策引擎系统

**文章系列**：
- [智能风控决策引擎系统可落地实现方案](https://blog.csdn.net/YouMing_Li/article/details/144166443)

**内容概览**：
- 决策引擎架构设计
- 规则引擎选型
- 模型引擎集成
- A/B 测试机制

---

## 🎯 推荐实施方案

### 方案 1：增强规则引擎（推荐）⭐⭐⭐⭐⭐

**组合**：
```
现有项目
  + Drools（替换/增强 decision-service）
  + Sentinel（保护 gateway 和 orchestrator）
```

**优势**：
- ✅ Drools 提供强大的规则管理能力
- ✅ Sentinel 提供流量保护
- ✅ 成熟稳定，社区支持好
- ✅ 渐进式优化，风险可控

**实施步骤**：
1. **第一阶段（1-2周）**：集成 Sentinel
   - Gateway 流量保护
   - Orchestrator 熔断降级
   - 配置监控面板

2. **第二阶段（2-4周）**：引入 Drools
   - 替换 decision-service 中的规则引擎
   - 实现决策表配置
   - 规则管理 API

3. **第三阶段（1-2个月）**：完善体系
   - 规则版本管理
   - A/B 测试支持
   - 规则命中报表

---

### 方案 2：完整参考 Radar

**组合**：
```
参考 Radar 架构
  - 引入 Groovy 动态规则引擎
  - 可视化规则管理界面
  - MongoDB + ES 存储方案
  - 完整的规则生命周期管理
```

**优势**：
- ✅ 功能完整，开箱即用
- ✅ Groovy 规则即时生效
- ✅ 可视化配置，易用性好
- ✅ 插件化设计，易扩展

**实施步骤**：
1. **第一阶段**：研究 Radar 架构
   - 阅读源码
   - 理解 Groovy 规则引擎
   - 测试在线 Demo

2. **第二阶段**：引入 Groovy 规则引擎
   - 决策服务支持 Groovy 脚本
   - 规则动态加载和热更新
   - 规则版本管理

3. **第三阶段**：构建可视化界面
   - 规则编辑器
   - 规则测试工具
   - 规则命中监控

---

### 方案 3：轻量级优化（快速见效）

**组合**：
```
- Easy Rules（简单规则场景）
- Sentinel（流量保护）
- 参考 Radar 的可视化规则编辑器设计
```

**优势**：
- ✅ 轻量灵活
- ✅ 易于定制
- ✅ 快速见效

**适用场景**：
- 规则复杂度不高
- 快速迭代需求
- 团队规模较小

---

## 📊 项目对比总结

| 项目 | 技术栈 | 成熟度 | 学习曲线 | 契合度 | 推荐场景 |
|------|--------|--------|----------|--------|----------|
| **Radar** | Spring Boot + Groovy + Mongo + Redis + ES | ⭐⭐⭐⭐⭐ | 中等 | ⭐⭐⭐⭐⭐ | 完整风控系统参考 |
| **Drools** | 纯 Java + RETE 算法 | ⭐⭐⭐⭐⭐ | 较陡 | ⭐⭐⭐⭐⭐ | 替换规则引擎 |
| **Easy Rules** | Java 8 + POJO | ⭐⭐⭐⭐ | 平缓 | ⭐⭐⭐⭐ | 简单规则场景 |
| **Sentinel** | Java + Spring Cloud | ⭐⭐⭐⭐⭐ | 平缓 | ⭐⭐⭐⭐⭐ | 流量保护 |
| **sunpeak/riskcontrol** | Java | ⭐⭐⭐ | 平缓 | ⭐⭐⭐ | 快速原型 |

---

## 📋 实施建议时间表

### 立即可做（1-2周）

1. **集成 Sentinel**
   - Gateway 流量保护
   - Orchestrator 熔断降级
   - 配置监控面板

2. **研究 Radar**
   - 阅读 Radar 源码
   - 测试在线 Demo
   - 总结架构设计要点

### 短期优化（1-2个月）

1. **调研 Drools**
   - 评估替换 decision-service 中的规则引擎
   - 实现 1-2 个典型规则作为 POC

2. **可视化规则管理**
   - 参考 Radar 设计规则编辑器原型
   - 实现规则配置 API

### 长期演进（3-6个月）

1. **构建完整规则管理平台**
   - 规则版本管理
   - 规则测试工具
   - 规则发布审核流程

2. **A/B 测试支持**
   - 规则灰度发布
   - 效果对比分析
   - 自动回滚机制

3. **规则命中报表**
   - 规则命中率统计
   - 规则效果分析
   - 优化建议

---

## 🔗 快速链接汇总

### 开源项目
- **Radar**: https://github.com/wfh45678/radar
- **Drools**: https://github.com/kiegroup/drools
- **Sentinel**: https://github.com/alibaba/Sentinel
- **Easy Rules**: https://github.com/dvgaba/easy-rules
- **sunpeak/riskcontrol**: https://github.com/sunpeak/riskcontrol
- **risk-talk**: https://github.com/aalansehaiyang/risk-talk

### 学习资源
- [有赞风控规则引擎实践](https://www.cnblogs.com/jpfss/p/10869920.html)
- [复杂风控场景下，如何打造一款高效的规则引擎 - 美团](https://tech.meituan.com/2020/05/14/meituan-security-zeus.html)
- [Spring Boot with Drools Rules Engine](https://medium.com/@tobintom/spring-boot-with-drools-rules-engine-d73e1af3c411)
- [智能风控决策引擎系统可落地实现方案](https://blog.csdn.net/YouMing_Li/article/details/144166443)

### 工具文档
- **Drools 官方文档**: https://docs.drools.org/
- **Sentinel 官方文档**: https://sentinelguard.io/zh-cn/docs/
- **Easy Rules 文档**: https://github.com/j-easy/easy-rules/wiki

---

## 💡 最后建议

**优先级排序**：
1. **Sentinel**（流量保护，立即见效）
2. **Drools**（规则引擎核心能力）
3. **Radar 研究**（架构设计参考）
4. **Easy Rules**（补充简单规则场景）

**风险控制**：
- 采用渐进式优化策略
- 新旧系统并行运行
- 充分测试后再切换
- 保留回滚机制

**团队建设**：
- 组织技术分享会
- 建立风控知识库
- 培养规则引擎专家
- 持续跟进开源社区

---

**文档版本**：v1.0
**最后更新**：2026-01-15
**维护者**：Risk Control Team
