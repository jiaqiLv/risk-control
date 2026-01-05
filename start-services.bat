@echo off
REM Windows 服务启动脚本

echo =========================================
echo   启动 Java 服务
echo =========================================
echo.

REM 检查是否已编译
if not exist "graph-service\build\libs\graph-service-1.0.0-SNAPSHOT.jar" (
    echo [错误] 项目未编译
    echo 请先运行: build.bat
    pause
    exit /b 1
)

echo [提示] 请在 3 个独立的终端窗口中运行以下命令:
echo.
echo ----------------------------------------
echo 终端 1 - Graph Service (端口 9091)
echo ----------------------------------------
echo   cd graph-service
echo   gradlew bootRun
echo   (或: java -jar build\libs\graph-service-1.0.0-SNAPSHOT.jar)
echo.
echo ----------------------------------------
echo 终端 2 - Feature Service (端口 9092)
echo ----------------------------------------
echo   cd feature-service
echo   gradlew bootRun
echo   (或: java -jar build\libs\feature-service-1.0.0-SNAPSHOT.jar)
echo.
echo ----------------------------------------
echo 终端 3 - Risk Gateway (端口 8080)
echo ----------------------------------------
echo   cd risk-gateway
echo   gradlew bootRun
echo   (或: java -jar build\libs\risk-gateway-1.0.0-SNAPSHOT.jar)
echo.
echo =========================================
echo.

REM 询问是否自动启动（使用 start 命令打开新窗口）
set /p AUTO="是否自动在新窗口启动所有服务? (Y/N): "

if /i "%AUTO%"=="Y" (
    echo.
    echo 正在启动服务...
    echo.

    start "Graph Service" cmd /k "cd graph-service && gradlew bootRun"
    timeout /t 5 /nobreak >nul

    start "Feature Service" cmd /k "cd feature-service && gradlew bootRun"
    timeout /t 5 /nobreak >nul

    start "Risk Gateway" cmd /k "cd risk-gateway && gradlew bootRun"

    echo.
    echo [✓] 所有服务已在新窗口中启动
    echo.
    echo 等待约 30 秒让服务完全启动...
    timeout /t 30 /nobreak

    echo.
    echo 测试服务健康状态...
    echo.

    curl -s http://localhost:8091/actuator/health
    echo.
    curl -s http://localhost:8092/actuator/health
    echo.
    curl -s http://localhost:8080/actuator/health
    echo.

    echo.
    echo [完成] 服务已启动！
    echo.
) else (
    echo.
    echo 请手动在 3 个终端中启动服务
    echo.
)

pause
