# Nacos PostgreSQL 集成指南

## 前置步骤

### 1. 初始化数据库

在启动Nacos之前，需要先在PostgreSQL中创建Nacos所需的表结构。

#### 方式一：手动执行（推荐）

使用Docker exec进入PostgreSQL容器并执行初始化脚本：

```bash
# 启动PostgreSQL
docker-compose up -d postgres

# 等待PostgreSQL就绪后，执行初始化脚本
docker exec -i risk-control-postgres psql -U postgres -d risk_control < C:\JaredLyu\OnTheWay\risk-control\infra\nacos\schema.sql
```

#### 方式二：使用psql客户端

如果你安装了PostgreSQL客户端工具：

```bash
psql -h localhost -U postgres -d risk_control -f C:\JaredLyu\OnTheWay\risk-control\infra\nacos\schema.sql
```

### 2. 验证数据库初始化

检查表是否创建成功：

```bash
docker exec -it risk-control-postgres psql -U postgres -d risk_control -c "\dt"
```

应该能看到以下表：
- config_info
- config_info_aggr
- config_info_beta
- config_info_tag
- config_tags_relation
- group_capacity
- his_config_info
- tenant_capacity
- tenant_info
- users
- roles
- permissions

### 3. 启动所有服务

```bash
cd C:\JaredLyu\OnTheWay\risk-control\infra
docker-compose up -d
```

## 访问Nacos

启动成功后，访问Nacos控制台：

- **地址**: http://localhost:8848/nacos
- **用户名**: nacos
- **密码**: nacos

## 配置说明

### 数据库连接

Nacos将连接到以下PostgreSQL实例：
- **主机**: postgres (Docker网络内部)
- **端口**: 5432
- **数据库**: risk_control
- **用户**: postgres
- **密码**: postgres

### 数据持久化

- **配置数据**: 存储在PostgreSQL数据库中
- **日志文件**: `C:\JaredLyu\OnTheWay\risk-control\infra\nacos\logs`
- **数据文件**: `C:\JaredLyu\OnTheWay\risk-control\infra\nacos\data`

### 服务依赖

Nacos配置了`depends_on`，会等待PostgreSQL健康检查通过后再启动，确保数据库连接正常。

## 故障排查

### Nacos无法启动

1. 检查PostgreSQL是否正常运行：
   ```bash
   docker-compose ps postgres
   ```

2. 查看Nacos日志：
   ```bash
   docker logs risk-control-nacos
   ```

3. 检查数据库表是否正确创建：
   ```bash
   docker exec -it risk-control-postgres psql -U postgres -d risk_control -c "\dt"
   ```

### 数据库连接失败

确保：
1. PostgreSQL已启动并通过健康检查
2. 数据库表已正确创建
3. 网络连接正常（都在risk-control-network中）

## 修改默认密码

默认的Nacos用户名和密码都是`nacos`，建议登录后立即修改：

1. 登录Nacos控制台
2. 进入"权限控制" -> "用户列表"
3. 找到nacos用户并修改密码
4. 同时需要更新docker-compose.yml中的认证配置
