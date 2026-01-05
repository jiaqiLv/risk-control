#!/bin/bash

# API 测试脚本

BASE_URL="http://localhost:8080"

echo "========================================="
echo "  风险评分 API 测试"
echo "========================================="
echo ""

# 测试 1: 正常请求
echo "测试 1: 正常风险评分请求"
echo "----------------------------------------"
curl -X POST "${BASE_URL}/api/v1/risk/score" \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt_test_001",
    "subjectType": "user",
    "subjectId": "user_12345",
    "transactionAmount": 1500.00,
    "currency": "USD",
    "merchantId": "merchant_001",
    "deviceId": "device_abc",
    "ipAddress": "192.168.1.100",
    "modelVersion": "v1"
  }' | jq .
echo ""
echo ""

# 测试 2: 幂等性测试（相同 eventId）
echo "测试 2: 幂等性测试（相同 eventId 应返回缓存）"
echo "----------------------------------------"
curl -X POST "${BASE_URL}/api/v1/risk/score" \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt_test_001",
    "subjectType": "user",
    "subjectId": "user_12345",
    "transactionAmount": 1500.00
  }' | jq .
echo ""
echo ""

# 测试 3: 高风险交易
echo "测试 3: 高风险交易（大额金额）"
echo "----------------------------------------"
curl -X POST "${BASE_URL}/api/v1/risk/score" \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt_test_002",
    "subjectType": "user",
    "subjectId": "user_99999",
    "transactionAmount": 50000.00,
    "currency": "USD",
    "ipAddress": "192.168.1.100"
  }' | jq .
echo ""
echo ""

# 测试 4: 健康检查
echo "测试 4: 服务健康检查"
echo "----------------------------------------"
echo "Risk Gateway:"
curl -s "${BASE_URL}/actuator/health" | jq .
echo ""
echo "Graph Service:"
curl -s "http://localhost:8091/actuator/health" | jq .
echo ""
echo "Feature Service:"
curl -s "http://localhost:8092/actuator/health" | jq .
echo ""
echo ""

# 测试 5: Prometheus 指标
echo "测试 5: Prometheus 指标"
echo "----------------------------------------"
curl -s "${BASE_URL}/actuator/prometheus" | head -20
echo ""
echo ""

echo "========================================="
echo "  测试完成"
echo "========================================="
echo ""
echo "查看 Kafka 消息:"
echo "  docker exec -it risk-control-kafka kafka-console-consumer.sh \\"
echo "    --bootstrap-server localhost:9092 \\"
echo "    --topic risk_score_events \\"
echo "    --from-beginning"
echo ""
echo "查看审计日志:"
echo "  docker exec -it risk-control-postgres psql -U postgres -d risk_control \\"
echo "    -c \"SELECT * FROM risk_request_audit ORDER BY created_at DESC LIMIT 5;\""
