#!/bin/bash

# 实时图欺诈检测系统 - 快速启动脚本

set -e

echo "========================================="
echo "  实时图欺诈检测系统 - 启动脚本"
echo "========================================="
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查 Docker
echo -e "${YELLOW}1. 检查 Docker 环境...${NC}"
if ! command -v docker &> /dev/null; then
    echo -e "${RED}错误: Docker 未安装${NC}"
    exit 1
fi

if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    echo -e "${RED}错误: Docker Compose 未安装${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Docker 环境检查通过${NC}"
echo ""

# 启动基础设施
echo -e "${YELLOW}2. 启动基础设施 (PostgreSQL, Redis, Kafka)...${NC}"
docker-compose up -d

echo -e "${GREEN}✓ 基础设施已启动${NC}"
echo ""

# 等待服务健康
echo -e "${YELLOW}3. 等待服务就绪...${NC}"
sleep 10

# 检查 PostgreSQL
echo -n "  检查 PostgreSQL..."
until docker exec risk-control-postgres pg_isready -U postgres &> /dev/null; do
    echo -n "."
    sleep 2
done
echo -e " ${GREEN}✓${NC}"

# 检查 Redis
echo -n "  检查 Redis..."
until docker exec risk-control-redis redis-cli ping &> /dev/null; do
    echo -n "."
    sleep 2
done
echo -e " ${GREEN}✓${NC}"

# 检查 Kafka
echo -n "  检查 Kafka..."
until docker exec risk-control-kafka kafka-topics.sh --bootstrap-server localhost:9092 --list &> /dev/null; do
    echo -n "."
    sleep 3
done
echo -e " ${GREEN}✓${NC}"
echo ""

# 编译项目
echo -e "${YELLOW}4. 编译项目...${NC}"
./gradlew clean build -x test
echo -e "${GREEN}✓ 项目编译完成${NC}"
echo ""

# 启动服务
echo -e "${YELLOW}5. 启动 Java 服务...${NC}"
echo "请在新终端中运行以下命令："
echo ""
echo -e "${GREEN}# Terminal 1: Graph Service${NC}"
echo "cd graph-service && ../gradlew bootRun"
echo ""
echo -e "${GREEN}# Terminal 2: Feature Service${NC}"
echo "cd feature-service && ../gradlew bootRun"
echo ""
echo -e "${GREEN}# Terminal 3: Risk Gateway${NC}"
echo "cd risk-gateway && ../gradlew bootRun"
echo ""

# 等待用户输入
read -p "服务启动完成后按 Enter 继续..."

# 验证服务
echo -e "${YELLOW}6. 验证服务状态...${NC}"
echo ""

echo -n "  检查 Graph Service (http://localhost:8091/actuator/health)..."
if curl -s http://localhost:8091/actuator/health | grep -q "UP"; then
    echo -e " ${GREEN}✓${NC}"
else
    echo -e " ${RED}✗${NC}"
fi

echo -n "  检查 Feature Service (http://localhost:8092/actuator/health)..."
if curl -s http://localhost:8092/actuator/health | grep -q "UP"; then
    echo -e " ${GREEN}✓${NC}"
else
    echo -e " ${RED}✗${NC}"
fi

echo -n "  检查 Risk Gateway (http://localhost:8080/actuator/health)..."
if curl -s http://localhost:8080/actuator/health | grep -q "UP"; then
    echo -e " ${GREEN}✓${NC}"
else
    echo -e " ${RED}✗${NC}"
fi
echo ""

# 测试 API
echo -e "${YELLOW}7. 测试风险评分 API...${NC}"
echo ""
echo "执行以下命令测试："
echo ""
echo -e "${GREEN}curl -X POST http://localhost:8080/api/v1/risk/score \\\\${NC}"
echo "  -H 'Content-Type: application/json' \\"
echo "  -d '{"
echo "    \"eventId\": \"evt_test_001\","
echo "    \"subjectType\": \"user\","
echo "    \"subjectId\": \"user_12345\","
echo "    \"transactionAmount\": 1500.00"
echo "  }'"
echo ""

echo -e "${GREEN}=========================================${NC}"
echo -e "${GREEN}  启动完成！${NC}"
echo -e "${GREEN}=========================================${NC}"
echo ""
echo "查看日志："
echo "  docker-compose logs -f"
echo ""
echo "停止所有服务："
echo "  docker-compose down"
echo "  # 或"
echo "  ./stop.sh"
