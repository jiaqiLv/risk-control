# Windows PowerShell API 测试脚本

$BASE_URL = "http://localhost:8080"

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  风险评分 API 测试" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

# 测试 1: 正常请求
Write-Host "测试 1: 正常风险评分请求" -ForegroundColor Green
Write-Host "----------------------------------------" -ForegroundColor Yellow

$body1 = @{
    eventId = "evt_test_001"
    subjectType = "user"
    subjectId = "user_12345"
    transactionAmount = 1500.00
    currency = "USD"
    merchantId = "merchant_001"
    deviceId = "device_abc"
    ipAddress = "192.168.1.100"
    modelVersion = "v1"
} | ConvertTo-Json -Depth 10

try {
    $response1 = Invoke-RestMethod -Uri "$BASE_URL/api/v1/risk/score" `
        -Method POST `
        -ContentType "application/json" `
        -Body $body1 `
        -ErrorAction Stop

    $response1 | ConvertTo-Json -Depth 10 | Write-Host
} catch {
    Write-Host "错误: $_" -ForegroundColor Red
}

Write-Host ""
Write-Host ""

# 测试 2: 幂等性测试
Write-Host "测试 2: 幂等性测试（相同 eventId 应返回缓存）" -ForegroundColor Green
Write-Host "----------------------------------------" -ForegroundColor Yellow

$body2 = @{
    eventId = "evt_test_001"
    subjectType = "user"
    subjectId = "user_12345"
    transactionAmount = 1500.00
} | ConvertTo-Json

try {
    $response2 = Invoke-RestMethod -Uri "$BASE_URL/api/v1/risk/score" `
        -Method POST `
        -ContentType "application/json" `
        -Body $body2 `
        -ErrorAction Stop

    $response2 | ConvertTo-Json -Depth 10 | Write-Host
} catch {
    Write-Host "错误: $_" -ForegroundColor Red
}

Write-Host ""
Write-Host ""

# 测试 3: 高风险交易
Write-Host "测试 3: 高风险交易（大额金额）" -ForegroundColor Green
Write-Host "----------------------------------------" -ForegroundColor Yellow

$body3 = @{
    eventId = "evt_test_002"
    subjectType = "user"
    subjectId = "user_99999"
    transactionAmount = 50000.00
    currency = "USD"
    ipAddress = "192.168.1.100"
} | ConvertTo-Json

try {
    $response3 = Invoke-RestMethod -Uri "$BASE_URL/api/v1/risk/score" `
        -Method POST `
        -ContentType "application/json" `
        -Body $body3 `
        -ErrorAction Stop

    $response3 | ConvertTo-Json -Depth 10 | Write-Host
} catch {
    Write-Host "错误: $_" -ForegroundColor Red
}

Write-Host ""
Write-Host ""

# 测试 4: 健康检查
Write-Host "测试 4: 服务健康检查" -ForegroundColor Green
Write-Host "----------------------------------------" -ForegroundColor Yellow

Write-Host "Risk Gateway:" -ForegroundColor Cyan
try {
    $health1 = Invoke-RestMethod -Uri "$BASE_URL/actuator/health" -ErrorAction Stop
    $health1 | ConvertTo-Json -Depth 10 | Write-Host
} catch {
    Write-Host "错误: $_" -ForegroundColor Red
}

Write-Host ""
Write-Host "Graph Service:" -ForegroundColor Cyan
try {
    $health2 = Invoke-RestMethod -Uri "http://localhost:8091/actuator/health" -ErrorAction Stop
    $health2 | ConvertTo-Json -Depth 10 | Write-Host
} catch {
    Write-Host "错误: $_" -ForegroundColor Red
}

Write-Host ""
Write-Host "Feature Service:" -ForegroundColor Cyan
try {
    $health3 = Invoke-RestMethod -Uri "http://localhost:8092/actuator/health" -ErrorAction Stop
    $health3 | ConvertTo-Json -Depth 10 | Write-Host
} catch {
    Write-Host "错误: $_" -ForegroundColor Red
}

Write-Host ""
Write-Host ""

# 测试 5: Prometheus 指标
Write-Host "测试 5: Prometheus 指标（前 20 行）" -ForegroundColor Green
Write-Host "----------------------------------------" -ForegroundColor Yellow

try {
    $metrics = Invoke-WebRequest -Uri "$BASE_URL/actuator/prometheus" -ErrorAction Stop
    $metrics.Content -split "`n" | Select-Object -First 20 | ForEach-Object { Write-Host $_ }
} catch {
    Write-Host "错误: $_" -ForegroundColor Red
}

Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  测试完成" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "查看 Kafka 消息:" -ForegroundColor Yellow
Write-Host "  docker exec -it risk-control-kafka kafka-console-consumer.bat --bootstrap-server localhost:9092 --topic risk_score_events --from-beginning"
Write-Host ""
Write-Host "查看审计日志:" -ForegroundColor Yellow
Write-Host "  docker exec -it risk-control-postgres psql -U postgres -d risk_control -c `"SELECT * FROM risk_request_audit ORDER BY created_at DESC LIMIT 5;`""
Write-Host ""
