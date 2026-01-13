# 配置指南 - Orchestrator Service

## 当前配置

### Python 服务连接

```yaml
orchestrator:
  python-inference-host: 10.60.38.173
  python-inference-port: 49094
  python-inference-timeout-ms: 5000
  mock-mode: false
  mode: HYBRID
  enable-fallback: true
```

**说明**：
- Python 服务地址：`10.60.38.173:49094`
- 超时时间：5秒
- 模式：混合模式（规则 + 模型）
- 降级：启用（模型失败时降级到规则）

---

## 模式切换

### 1. Mock 模式（本地测试，无需 Python 服务）

**用途**：
- 本地开发和测试
- 无需真实 Python 服务
- 使用简单规则模拟决策

**配置**：
```yaml
orchestrator:
  mock-mode: true
```

**启动**：
```bash
mvn spring-boot:run
```

**测试**：
```bash
test-orchestrator.bat
```

---

### 2. 真实模式 - MODEL_ONLY（纯 Python 模型）

**用途**：
- 只使用 Python 模型决策
- 不使用规则引擎
- 最依赖 Python 服务

**配置**：
```yaml
orchestrator:
  mock-mode: false
  mode: MODEL_ONLY
  enable-fallback: false  # 禁用降级，纯依赖模型
```

**启动**：
```bash
mvn spring-boot:run
```

**测试**：
```bash
test-python-connection.bat
```

---

### 3. 真实模式 - HYBRID（混合模式，推荐）

**用途**：
- 综合使用规则和模型
- 模型权重 70%，规则权重 30%
- 模型失败时降级到规则

**配置**：
```yaml
orchestrator:
  mock-mode: false
  mode: HYBRID
  enable-fallback: true
```

**优势**：
- 准确度高（模型为主）
- 可靠性强（规则兜底）
- 性能平衡

---

### 4. 真实模式 - RULES_ONLY（纯规则模式）

**用途**：
- 只使用规则引擎
- 不调用 Python 服务
- 性能最优

**配置**：
```yaml
orchestrator:
  mock-mode: false
  mode: RULES_ONLY
```

---

## 快速切换脚本

### 切换到 Mock 模式

创建 `switch-to-mock.bat`：
```bat
@echo off
echo Switching to Mock mode...
powershell -Command "(Get-Content application.yml) -replace 'mock-mode: false', 'mock-mode: true' | Set-Content application.yml"
echo Done! Now starting in Mock mode...
mvn spring-boot:run
```

### 切换到真实模式

创建 `switch-to-real.bat`：
```bat
@echo off
echo Switching to Real mode...
powershell -Command "(Get-Content application.yml) -replace 'mock-mode: true', 'mock-mode: false' | Set-Content application.yml"
echo Done! Now starting in Real mode...
mvn spring-boot:run
```

---

## 连接测试

### 测试 Python 服务连接

```bash
# 1. 确保 Orchestrator 已启动
cd services/risk-orchestrator-service
mvn spring-boot:run

# 2. 在另一个终端运行测试
test-python-connection.bat
```

### 查看日志

```bash
# 查看 gRPC 通信日志
tail -f logs/orchestrator-service.log
```

**关键日志**：
```
INFO  - Calling Python model for transaction: test_python_001
INFO  - Python model response: decision=APPROVE, score=0.23
INFO  - Evaluation completed: transactionId=test_python_001, decision=APPROVE, score=0.23
```

---

## 故障排查

### 问题 1：无法连接到 Python 服务

**症状**：
```
ERROR - Python model evaluation failed: io.grpc.netty.shaded.io.netty.channel.AbstractChannel$AnnotatedConnectException: connect timed out
```

**解决方案**：

1. **检查网络连通性**
   ```bash
   ping 10.60.38.173
   telnet 10.60.38.173 49094
   ```

2. **检查 Python 服务是否运行**
   - 登录到 `10.60.38.173` 服务器
   - 检查 Python 服务进程
   - 检查端口 49094 是否监听

3. **检查防火墙**
   - 确保端口 49094 开放
   - 检查安全组规则

4. **启用降级模式**
   ```yaml
   orchestrator:
     enable-fallback: true  # 模型失败时降级到规则
   ```

---

### 问题 2：Python 服务响应慢

**症状**：
```
ERROR - Python model evaluation failed: io.grpc.DeadlineExceeded
```

**解决方案**：

1. **增加超时时间**
   ```yaml
   orchestrator:
     python-inference-timeout-ms: 10000  # 10秒
   ```

2. **检查 Python 服务性能**
   - 查看 Python 服务日志
   - 检查 CPU/内存使用
   - 优化模型推理速度

---

### 问题 3：Proto 文件不匹配

**症状**：
```
ERROR - Failed to call Python model: io.grpc.StatusRuntimeException: UNIMPLEMENTED
```

**解决方案**：

1. **确认 Proto 文件版本一致**
   - Java 端：`libs/contracts/src/main/proto/risk-infra.proto`
   - Python 端：使用相同的 proto 文件

2. **重新编译 Java 端**
   ```bash
   cd libs/contracts
   mvn clean install
   ```

3. **重新生成 Python 代码**
   ```bash
   python -m grpc_tools.protoc -I. --python_out=. --grpc_python_out=. risk-infra.proto
   ```

---

## 性能调优

### 1. 调整超时时间

根据网络延迟和模型推理时间调整：

```yaml
orchestrator:
  python-inference-timeout-ms: 5000  # 可调整为 3000-10000
```

### 2. 选择合适的模式

| 模式 | 准确度 | 性能 | 可靠性 | 推荐场景 |
|------|--------|------|--------|----------|
| MODEL_ONLY | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐ | Python服务稳定 |
| HYBRID | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | 生产环境推荐 |
| RULES_ONLY | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | 高性能场景 |

### 3. 启用降级

```yaml
orchestrator:
  enable-fallback: true  # 强烈推荐
```

---

## 监控指标

### 关键指标

在日志中搜索：

1. **请求量**
   ```
   grep "Evaluating transaction" logs/orchestrator-service.log | wc -l
   ```

2. **模型成功率**
   ```
   grep "Python model response" logs/orchestrator-service.log | wc -l
   grep "Python model evaluation failed" logs/orchestrator-service.log | wc -l
   ```

3. **降级率**
   ```
   grep "fallback_used" logs/orchestrator-service.log | grep "model_to_rules"
   ```

4. **平均延迟**
   ```
   grep "Evaluation completed" logs/orchestrator-service.log
   ```

---

## 配置文件位置

- **主配置**：`src/main/resources/application.yml`
- **Mock配置**：`src/main/resources/application-mock.yml`
- **日志**：`logs/orchestrator-service.log`

---

## 下一步

1. ✅ 配置已更新为连接真实 Python 服务
2. ✅ 超时时间设置为 5 秒
3. ✅ 降级策略已启用
4. ✅ 测试脚本已创建

**现在可以**：
1. 启动 Orchestrator Service
2. 运行 `test-python-connection.bat` 测试连接
3. 查看日志验证 gRPC 通信

需要帮助？
- 查看日志：`logs/orchestrator-service.log`
- 检查配置：`application.yml`
- 测试连接：`test-python-connection.bat`
