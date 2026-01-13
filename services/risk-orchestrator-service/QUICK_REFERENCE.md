# 快速参考 - Orchestrator Service

## 当前配置

```yaml
Python 服务: 10.60.38.173:49094
模式: HYBRID (混合模式)
超时: 5秒
降级: 启用
Mock模式: 关闭
```

---

## 一键测试

### 1. 启动服务
```bash
cd services/risk-orchestrator-service
start.bat
```

### 2. 测试连接
```bash
test-python-connection.bat
```

### 3. 查看日志
```bash
tail -f logs/orchestrator-service.log
```

---

## 快速切换模式

### 切换到 Mock 模式
编辑 `application.yml`:
```yaml
mock-mode: true
```

### 切换到真实模式
编辑 `application.yml`:
```yaml
mock-mode: false
python-inference-host: 10.60.38.173
python-inference-port: 49094
```

---

## 常用命令

```bash
# 编译
mvn clean compile

# 运行
mvn spring-boot:run

# 打包
mvn clean package

# 测试健康检查
curl http://localhost:8081/api/v1/health

# 测试评估接口
curl -X POST http://localhost:8081/api/v1/evaluate \
  -H "Content-Type: application/json" \
  -d '{"transactionId":"test001","userId":"user001","eventTimestamp":1736608800000,"amount":299.99,"currency":"USD"}'
```

---

## 故障排查

| 问题 | 解决方案 |
|------|----------|
| 连接超时 | 检查网络：`ping 10.60.38.173` |
| 端口不通 | 检查防火墙和安全组 |
| Proto不匹配 | 重新编译 contracts 模块 |
| 响应慢 | 增加 `python-inference-timeout-ms` |

---

## 配置文件

- **主配置**: `src/main/resources/application.yml`
- **日志**: `logs/orchestrator-service.log`
- **端口**: 8081

---

## 与 Gateway 对接

Gateway 配置 (`services/gateway-service/src/main/resources/application.yml`):
```yaml
gateway:
  orchestrator-base-url: http://localhost:8081
```

---

## 支持

- 详细配置: `CONFIG_GUIDE.md`
- 完整文档: `README.md`
- 实现总结: `IMPLEMENTATION_SUMMARY.md`
