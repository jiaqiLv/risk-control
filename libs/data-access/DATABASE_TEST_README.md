# 数据库连接测试说明

## 概述

本项目提供了全面的数据库连接测试，基于真实的实体表结构进行测试，包括：
- `transactions` - 交易主表
- `transaction_attributes` - 交易属性表
- `transaction_identity` - 交易身份信息表

## 测试文件

### 1. QuickConnectionTest（快速连接测试）
- **文件**: `src/test/java/com/risk/data/QuickConnectionTest.java`
- **用途**: 快速验证数据库连接和基本表结构
- **测试内容**:
  - 数据库连接是否成功
  - 数据库版本查询
  - 检查三个核心表是否存在
  - 各表结构验证
  - 交易记录数统计
  - 决策分布查询

### 2. DatabaseConnectionTest（完整数据库测试）
- **文件**: `src/test/java/com/risk/data/DatabaseConnectionTest.java`
- **用途**: 全面的数据库结构和配置测试
- **测试内容**:
  - R2DBC 连接池配置
  - 数据库连接和版本
  - 表结构完整性检查
  - 表索引验证
  - 外键约束检查
  - 数据类型正确性验证
  - JSONB 字段检查
  - 数据统计查询
  - 决策枚举值验证

### 3. EntityRepositoryTest（实体 CRUD 测试）- 新增
- **文件**: `src/test/java/com/risk/data/EntityRepositoryTest.java`
- **用途**: 测试实体的创建、查询、更新、删除操作
- **测试内容**:
  - 仓库注入测试
  - 创建交易实体
  - 查询交易记录（按 ID、用户、决策）
  - 更新交易决策和风险评分
  - 统计查询
  - 批量查询
  - 删除交易

## 配置文件

测试配置文件位于: `src/test/resources/application-test.yml`

### 默认配置
```yaml
数据库地址: localhost:5432
数据库名称: riskcontrol
用户名: postgres
密码: postgres
```

### 自定义配置

**方法 1: 环境变量**
```bash
export DB_USERNAME=your_username
export DB_PASSWORD=your_password
```

**方法 2: 修改配置文件**
编辑 `src/test/resources/application-test.yml`

## 运行测试

### 前置条件

1. 确保 PostgreSQL 数据库正在运行
2. 数据库 `riskcontrol` 已创建
3. 用户权限配置正确
4. 数据库表已创建（通过 Liquibase 迁移或手动创建）

### 运行方式

#### 方法 1: 使用批处理脚本（推荐）
```bash
cd libs\data-access
run-db-test.bat
```

#### 方法 2: 使用 Maven 命令

**运行快速连接测试**:
```bash
cd libs/data-access
mvn test -Dtest=QuickConnectionTest
```

**运行完整数据库测试**:
```bash
cd libs/data-access
mvn test -Dtest=DatabaseConnectionTest
```

**运行实体 CRUD 测试**:
```bash
cd libs/data-access
mvn test -Dtest=EntityRepositoryTest
```

**运行所有测试**:
```bash
cd libs/data-access
mvn test
```

#### 方法 3: 在 IDE 中运行

- 在 IDE（如 IntelliJ IDEA 或 Eclipse）中打开测试类
- 右键点击测试类或测试方法
- 选择 "Run" 或 "Debug"

## 实体表结构

### transactions 表（交易主表）
- `id` - 主键（自增）
- `transaction_id` - 交易ID（业务主键，唯一）
- `user_id` - 用户ID
- `merchant_id` - 商户ID
- `event_timestamp` - 事件时间戳（毫秒）
- `amount` - 交易金额（numeric）
- `product_cd` - 产品代码（如 W, C 等）
- `decision` - 风险决策（APPROVE, REVIEW, REJECT, PENDING）
- `risk_score` - 风险评分（0.0 - 1.0）
- `is_fraud` - 是否为欺诈交易
- `created_at` - 创建时间
- `updated_at` - 更新时间

### transaction_attributes 表（交易属性表）
- `id` - 主键（自增）
- `transaction_id` - 关联交易ID（外键）
- `card1` - `card6` - 卡片信息
- `addr1`, `addr2` - 地址信息
- `dist1`, `dist2` - 距离信息
- `p_emaildomain`, `r_emaildomain` - 邮箱域名
- `extended_attributes` - 扩展属性（JSONB 类型）
- `created_at`, `updated_at` - 时间戳

### transaction_identity 表（交易身份信息表）
- `id` - 主键（自增）
- `transaction_id` - 关联交易ID（外键）
- `id_01` - `id_22` - 22个身份信息字段
- `device_type`, `device_info` - 设备信息
- `created_at`, `updated_at` - 时间戳

## 常见问题

### 1. 连接失败
**错误**: `Cannot connect to database`

**解决方案**:
- 检查 PostgreSQL 是否运行: `pg_isready` 或 `psql -U postgres -h localhost`
- 检查端口是否正确 (默认 5432)
- 检查防火墙设置

### 2. 认证失败
**错误**: `FATAL: password authentication failed`

**解决方案**:
- 检查用户名和密码是否正确
- 使用环境变量设置正确的凭据:
  ```bash
  export DB_USERNAME=your_username
  export DB_PASSWORD=your_password
  ```

### 3. 数据库不存在
**错误**: `FATAL: database "riskcontrol" does not exist`

**解决方案**:
```sql
-- 连接到 PostgreSQL
psql -U postgres -h localhost

-- 创建数据库
CREATE DATABASE riskcontrol;

-- 退出
\q
```

### 4. 表不存在
**警告**: `部分表缺失，找到 X 个表（预期 3 个）` 或 `缺少必需的表，可能需要运行 Liquibase 迁移`

**解决方案**:
运行 Liquibase 迁移创建表结构：
```bash
# 在项目根目录运行
mvn liquibase:update
```

或手动执行 SQL 脚本创建表结构。

### 5. CRUD 测试失败
**错误**: EntityRepositoryTest 中的创建、更新或删除操作失败

**可能原因**:
- 表不存在（参考问题 4）
- 数据库连接问题
- 权限不足

**解决方案**:
- 确保表已创建
- 检查数据库用户权限
- 查看详细错误日志

## 测试输出示例

### QuickConnectionTest 成功输出
```
=================================================
数据库连接快速测试
=================================================
✓ 数据库连接成功！
  连接工厂: ConnectionPool
✓ 测试查询成功！
✓ 数据库版本: PostgreSQL 15.x ...
✓ 连接已关闭
✓ 所有必需的表都存在 (transactions, transaction_attributes, transaction_identity)
=================================================
✓✓✓ 所有测试通过！数据库连接正常！✓✓✓
=================================================
```

### EntityRepositoryTest 成功输出
```
✓ 仓库注入成功
  TransactionR2dbcRepository: TransactionR2dbcRepository
  R2dbcEntityTemplate: R2dbcEntityTemplate
✓ 成功创建交易实体
  ID: 1
  TransactionID: TEST_TXN_1234567890
  UserID: TEST_USER_001
  Amount: 100.50
  Decision: PENDING
✓ 成功查询交易记录
  TransactionID: TEST_FIND_1234567890
  Decision: APPROVE
✓ 成功更新交易决策
  原决策: PENDING
  新决策: REVIEW
  风险评分: 0.75
✓ 统计查询成功
  总交易数: 0
✓ 批量查询成功
  表中暂无数据
```

### DatabaseConnectionTest 成功输出
```
✓ 数据库表查询成功
  现有表: transaction_attributes, transaction_identity, transactions
✓ transactions 表结构完整
  transaction_attributes 表: 16 列
  transaction_identity 表: 28 列
✓ 数据统计:
  transactions: 0 条
  transaction_attributes: 0 条
  transaction_identity: 0 条
✓ 决策枚举值:
  (暂无数据)
✓ transactions 表的索引:
  - idx_transaction_id
  - idx_user_id
  - idx_event_timestamp
  - idx_decision
  - idx_created_at
  - transactions_pkey
✓ 外键约束:
  transaction_attributes.transaction_id -> transactions
  transaction_identity.transaction_id -> transactions
✓ transactions 表关键字段类型:
  amount: numeric
  decision: character varying(20)
  event_timestamp: bigint
  risk_score: double precision
  transaction_id: character varying(64)
✓ transaction_attributes 表的 JSONB 字段:
  - extended_attributes (jsonb)
```

## Docker 环境

如果使用 Docker 运行 PostgreSQL：

### 启动 PostgreSQL 容器
```bash
docker run -d \
  --name riskcontrol-postgres \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=riskcontrol \
  -p 5432:5432 \
  postgres:15
```

### 检查容器状态
```bash
docker ps | grep riskcontrol-postgres
```

### 查看容器日志
```bash
docker logs riskcontrol-postgres
```

### 连接到数据库
```bash
docker exec -it riskcontrol-postgres psql -U postgres -d riskcontrol
```

## 注意事项

1. 测试会连接到真实的数据库，请确保不要在生产环境运行
2. 测试不会修改数据，只会执行只读查询
3. 首次运行可能需要更长时间（初始化连接池）
4. 如果使用不同的数据库配置，请更新 `application-test.yml`

## 后续步骤

测试通过后，你可以：
1. 运行其他集成测试
2. 执行 Liquibase 迁移创建表结构
3. 开发和测试其他数据访问功能
