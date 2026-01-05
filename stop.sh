#!/bin/bash

# 停止所有服务

echo "停止 Docker 基础设施..."
docker-compose down

echo "查找并停止 Java 进程..."
pkill -f "graph-service" || true
pkill -f "feature-service" || true
pkill -f "risk-gateway" || true

echo "所有服务已停止"
