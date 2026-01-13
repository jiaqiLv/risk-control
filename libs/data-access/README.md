# Data Access Layer

风险控制系统的共享数据访问层，提供实体模型、仓库接口和数据库配置。

## 📦 模块信息

- **模块名称**: data-access
- **版本**: 1.0.0-SNAPSHOT
- **依赖**: Spring Data JPA, Spring Data R2DBC, PostgreSQL, Liquibase

## 🏗️ 架构设计

### 技术栈选择

- **Spring Data JPA**: 用于复杂查询（同步）
- **Spring Data R2DBC**: 用于异步非阻塞写入（响应式）
- **Liquibase**: 数据库版本管理和迁移
- **PostgreSQL**: 关系型数据库
- **Lombok**: 减少样板代码

### 为什么同时使用 JPA 和 R2DBC？

1. **R2DBC（响应式）**:
   - ✅ 异步非阻塞，不阻塞主线程
   - ✅ 适合高并发写入场景
   - ✅ 支持流式处理（Flux/Mono）
   - ❌ 查询功能相对有限

2. **JPA（同步）**:
   - ✅ 功能丰富，支持复杂查询
   - ✅ 成熟稳定，生态系统完善
   - ✅ 支持复杂关联查询和原生 SQL
   - ❌ 同步阻塞，会阻塞线程

**使用策略**:
- 写入操作 → 使用 R2DBC（异步高性能）
- 复杂查询 → 使用 JPA（功能强大）
- 简单查询 → 使用 R2DBC（异步性能）

## 📁 目录结构

```
libs/data-access/
├── src/main/java/com/risk/data/
│   ├── entity/                              # 实体模型
│   │   ├── Decision.java                    # 决策枚举
│   │   ├── TransactionEntity.java           # 交易实体
│   │   ├── TransactionAttributeEntity.java  # 交易属性实体
│   │   └── TransactionIdentityEntity.java   # 身份信息实体
│   ├── repository/                          # 仓库接口
│   │   ├── TransactionRepository.java       # JPA 仓库（同步查询）
│   │   └── TransactionR2dbcRepository.java  # R2DBC 仓库（异步操作）
│   ├── dto/                                 # 数据传输对象
│   │   ├── TransactionDTO.java              # 交易 DTO
│   │   └── CsvImportResult.java             # CSV 导入结果
│   └── config/                              # 配置类
│       ├── JpaConfig.java                   # JPA 配置
│       └── R2dbcConfig.java                 # R2DBC 配置
├── src/main/resources/
│   ├── application.yml          # 数据库配置文件
│   └── db/changelog/
│       └── master.yaml                      # Liquibase 迁移脚本
└── pom.xml                                  # Maven 配置
```

## 🚀 快速开始

### 1. 在服务中使用

在需要使用数据库的服务（如 gateway-service）的 `pom.xml` 中添加依赖：

```xml
<dependency>
    <groupId>com.risk-control</groupId>
    <artifactId>data-access</artifactId>
    <version>${project.version}</version>
</dependency>
```

### 2. 配置数据库连接

在服务的 `application.yml` 中引入数据库配置：

```yaml
spring:
  config:
    import: classpath:application.yml

  # 如果需要覆盖配置
  r2dbc:
    url: r2dbc:postgresql://your-host:5432/riskcontrol
    username: your-username
    password: your-password
```

### 3. 启用 Liquibase 迁移

确保在主应用类上添加 `@LiquibaseDataSource` 或让 Liquibase 自动配置：

```java
@SpringBootApplication
@EnableLiquibase  // 如果需要手动启用
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
```

### 4. 使用仓库

**JPA 仓库（同步查询）**:

```java
@Service
public class TransactionQueryService {

    @Autowired
    private TransactionRepository transactionRepository;

    public List<TransactionEntity> findByUserId(String userId) {
        return transactionRepository.findByUserIdOrderByEventTimestampDesc(userId);
    }

    public Long countTotalTransactions() {
        return transactionRepository.countTotalTransactions();
    }
}
```

**R2DBC 仓库（异步写入）**:

```java
@Service
public class TransactionWriteService {

    @Autowired
    private TransactionR2dbcRepository transactionRepository;

    /**
     * 异步保存交易（非阻塞）
     */
    public Mono<TransactionEntity> saveAsync(TransactionEntity entity) {
        return transactionRepository.save(entity)
            .doOnSuccess(saved -> log.info("交易已保存: {}", saved.getTransactionId()))
            .doOnError(error -> log.error("保存失败", error));
    }

    /**
     * Fire-and-Forget 模式（不等待结果）
     */
    public void saveAndForget(TransactionEntity entity) {
        transactionRepository.save(entity)
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();  // 不等待结果
    }
}
```

## 📊 数据库表结构

### transactions（交易主表）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGSERIAL | 主键（自增） |
| transaction_id | VARCHAR(64) | 交易ID（业务主键，唯一） |
| user_id | VARCHAR(64) | 用户ID |
| event_timestamp | BIGINT | 事件时间戳（毫秒） |
| amount | NUMERIC(19,2) | 交易金额 |
| currency | VARCHAR(3) | 货币代码（默认 USD） |
| product_cd | VARCHAR(10) | 产品代码 |
| channel | VARCHAR(20) | 交易渠道 |
| decision | VARCHAR(20) | 风险决策结果 |
| risk_score | DOUBLE | 风险评分（0.0-1.0） |
| is_fraud | BOOLEAN | 是否为欺诈交易 |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |

**索引**:
- idx_transaction_id (transaction_id)
- idx_user_id (user_id)
- idx_event_timestamp (event_timestamp)
- idx_decision (decision)
- idx_created_at (created_at)

### transaction_attributes（交易属性表）

存储交易特征数据（card1-6, addr1-2, deviceInfo 等）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGSERIAL | 主键 |
| transaction_id | BIGINT | 关联交易ID（外键） |
| card1-6 | VARCHAR/INTEGER/DOUBLE | 卡片信息 |
| addr1-2 | VARCHAR(100) | 地址信息 |
| device_info | VARCHAR(200) | 设备信息 |
| extended_attributes | JSONB | 扩展属性（JSON 格式） |

### transaction_identity（身份信息表）

存储 IEEE-CIS identity.csv 数据

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGSERIAL | 主键 |
| transaction_id | BIGINT | 关联交易ID（外键） |
| id_01 ~ id_22 | VARCHAR(100) | 身份信息字段 |
| device_type | VARCHAR(50) | 设备类型 |
| device_info | VARCHAR(200) | 设备信息 |

## 🔧 配置说明

### R2DBC 连接池配置

```yaml
spring:
  r2dbc:
    pool:
      enabled: true
      initial-size: 5      # 初始连接数
      max-size: 20         # 最大连接数
      max-idle-time: 30m   # 最大空闲时间
      max-life-time: 1h    # 连接最大生命周期
```

**调优建议**:
- 高并发写入: initial-size=10, max-size=50
- 低并发查询: initial-size=2, max-size=10
- 批量导入: initial-size=20, max-size=100

### JPA 配置

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # 使用 Liquibase 管理 schema
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
```

### Liquibase 配置

```yaml
spring:
  liquibase:
    enabled: true
    change-log: classpath:db/changelog/master.yaml
    drop-first: false  # 生产环境必须为 false
```

## 🔍 使用示例

### 示例 1: 异步保存交易（在 Gateway 中）

```java
@Service
public class GatewayService {

    @Autowired
    private TransactionR2dbcRepository repository;

    public Mono<TransactionResponse> processTransaction(TransactionRequest request) {
        // 调用 Orchestrator
        return orchestratorClient.assess(request)
            .doOnNext(response -> {
                // 异步保存到数据库（不阻塞响应）
                TransactionEntity entity = TransactionEntity.builder()
                    .transactionId(request.getTransactionId())
                    .userId(request.getUserId())
                    .amount(request.getAmount())
                    .decision(response.getDecision())
                    .riskScore(response.getRiskScore())
                    .build();

                // Fire-and-Forget 模式
                repository.save(entity)
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe(
                        result -> log.info("已保存: {}", result.getTransactionId()),
                        error -> log.error("保存失败", error)
                    );
            });
    }
}
```

### 示例 2: 批量查询（使用 JPA）

```java
@Service
public class ReportService {

    @Autowired
    private TransactionRepository repository;

    public List<TransactionEntity> generateFraudReport(LocalDateTime start, LocalDateTime end) {
        Long startTime = start.toInstant(ZoneOffset.UTC).toEpochMilli();
        Long endTime = end.toInstant(ZoneOffset.UTC).toEpochMilli();

        return repository.findFraudTransactionsInTimeRange(startTime, endTime);
    }

    public Map<Decision, Long> countByDecision() {
        List<Object[]> results = repository.countByDecision();
        return results.stream()
            .collect(Collectors.toMap(
                row -> (Decision) row[0],
                row -> (Long) row[1]
            ));
    }
}
```

### 示例 3: CSV 批量导入（使用 R2DBC）

```java
@Service
public class CsvImportService {

    @Autowired
    private TransactionR2dbcRepository repository;

    public Mono<CsvImportResult> importFromCsv(MultipartFile csvFile) {
        return Mono.fromCallable(() -> {
            // 读取 CSV 文件
            List<TransactionEntity> entities = parseCsv(csvFile);

            // 批量异步保存
            return Flux.fromIterable(entities)
                .buffer(1000)  // 每1000条一批
                .flatMap(batch -> repository.saveAll(Flux.fromIterable(batch)))
                .collectList()
                .block();
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
```

## 🧪 测试

创建单元测试时，可以使用 `@DataJpaTest` 或 `@DataR2dbcTest`：

```java
@DataJpaTest
class TransactionRepositoryTest {

    @Autowired
    private TransactionRepository repository;

    @Test
    void testFindByTransactionId() {
        TransactionEntity entity = new TransactionEntity();
        entity.setTransactionId("test_001");
        repository.save(entity);

        Optional<TransactionEntity> found = repository.findByTransactionId("test_001");
        assertTrue(found.isPresent());
    }
}
```

## 🐛 常见问题

### 1. Liquibase 迁移失败

**问题**: 启动时报错 "Liquibase failed to update database"

**解决方案**:
```yaml
spring:
  liquibase:
    enabled: false  # 先禁用 Liquibase
```

然后手动执行 SQL 脚本，或检查数据库连接配置。

### 2. R2DBC 连接超时

**问题**: 连接数据库超时

**解决方案**:
```yaml
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/riskcontrol
    pool:
      initial-size: 2
      max-size: 10
      max-idle-time: 10m
```

### 3. Docker 容器连接问题

**问题**: 无法连接到 Docker PostgreSQL 容器

**解决方案**:
```yaml
# 使用容器名（如果在同一网络）
spring:
  r2dbc:
    url: r2dbc:postgresql://719c5bd933da:5432/riskcontrol

# 或者使用 localhost:5432（如果端口已映射）
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/riskcontrol
```

## 📚 参考资料

- [Spring Data JPA](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/)
- [Spring Data R2DBC](https://docs.spring.io/spring-data/r2dbc/docs/current/reference/html/)
- [Liquibase Documentation](https://docs.liquibase.com/)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)

## 📝 下一步

1. ✅ data-access 模块已完成
2. 🔄 创建 transaction-service
3. 🔄 集成到 gateway-service 和 orchestrator-service
4. 🔄 修改 txn-simulator 支持数据库持久化
5. 🔄 实现 CSV 批量导入功能

---

**最后更新**: 2026-01-11
**维护者**: Jared
**版本**: 1.0.0-SNAPSHOT
